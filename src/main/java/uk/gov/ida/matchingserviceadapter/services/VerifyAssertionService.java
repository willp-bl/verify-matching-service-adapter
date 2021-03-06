package uk.gov.ida.matchingserviceadapter.services;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import uk.gov.ida.matchingserviceadapter.domain.AssertionData;
import uk.gov.ida.matchingserviceadapter.exceptions.SamlResponseValidationException;
import uk.gov.ida.matchingserviceadapter.validators.ConditionsValidator;
import uk.gov.ida.matchingserviceadapter.validators.InstantValidator;
import uk.gov.ida.matchingserviceadapter.validators.SubjectValidator;
import uk.gov.ida.saml.core.transformers.AuthnContextFactory;
import uk.gov.ida.saml.core.transformers.VerifyMatchingDatasetUnmarshaller;
import uk.gov.ida.saml.core.transformers.inbound.Cycle3DatasetFactory;
import uk.gov.ida.saml.security.SamlAssertionsSignatureValidator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static uk.gov.ida.saml.core.validation.errors.GenericHubProfileValidationSpecification.MISMATCHED_ISSUERS;
import static uk.gov.ida.saml.core.validation.errors.GenericHubProfileValidationSpecification.MISMATCHED_PIDS;

public class VerifyAssertionService extends AssertionService {

    private enum AssertionType {AUTHN_ASSERTION, MDS_ASSERTION, CYCLE_3_ASSERTION}

    private String hubEntityId;
    private final VerifyMatchingDatasetUnmarshaller matchingDatasetUnmarshaller;
    private AuthnContextFactory authnContextFactory = new AuthnContextFactory();

    public VerifyAssertionService(InstantValidator instantValidator,
                                  SubjectValidator subjectValidator,
                                  ConditionsValidator conditionsValidator,
                                  SamlAssertionsSignatureValidator hubSignatureValidator,
                                  Cycle3DatasetFactory cycle3DatasetFactory,
                                  String hubEntityId,
                                  VerifyMatchingDatasetUnmarshaller matchingDatasetUnmarshaller) {
        super(instantValidator, subjectValidator, conditionsValidator, hubSignatureValidator, cycle3DatasetFactory);
        this.hubEntityId = hubEntityId;
        this.matchingDatasetUnmarshaller = matchingDatasetUnmarshaller;
    }


    @Override
    public void validate(String requestId, List<Assertion> assertions) {

        Map<AssertionType, List<Assertion>> assertionMap = assertions.stream()
                .collect(Collectors.groupingBy(this::classifyAssertion));

        List<Assertion> authnAssertions = assertionMap.get(AssertionType.AUTHN_ASSERTION);
        if (authnAssertions == null || authnAssertions.size() != 1) {
            throw new SamlResponseValidationException("Exactly one authn statement is expected.");
        }

        List<Assertion> mdsAssertions = assertionMap.get(AssertionType.MDS_ASSERTION);
        if (mdsAssertions == null || mdsAssertions.size() != 1) {
            throw new SamlResponseValidationException("Exactly one matching dataset assertion is expected.");
        }

        Assertion authnAssertion = authnAssertions.get(0);
        Assertion mdsAssertion = mdsAssertions.get(0);

        validateHubAssertion(authnAssertion, requestId, hubEntityId, IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        validateHubAssertion(mdsAssertion, requestId, hubEntityId, IDPSSODescriptor.DEFAULT_ELEMENT_NAME);

        assertionMap.getOrDefault(AssertionType.CYCLE_3_ASSERTION, emptyList()).forEach(a -> validateCycle3Assertion(a, requestId, hubEntityId));

        if (!mdsAssertion.getIssuer().getValue().equals(authnAssertion.getIssuer().getValue())) {
            throw new SamlResponseValidationException(MISMATCHED_ISSUERS);
        }

        if (!mdsAssertion.getSubject().getNameID().getValue().equals(authnAssertion.getSubject().getNameID().getValue())) {
            throw new SamlResponseValidationException(MISMATCHED_PIDS);
        }
    }

    @Override
    public AssertionData translate(List<Assertion> assertions) {
        Map<AssertionType, List<Assertion>> assertionMap = assertions.stream()
                .collect(Collectors.groupingBy(this::classifyAssertion));

        AuthnStatement authnStatement = assertionMap.get(AssertionType.AUTHN_ASSERTION).get(0).getAuthnStatements().get(0);
        String levelOfAssurance = authnStatement.getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef();
        Assertion mdsAssertion = assertionMap.get(AssertionType.MDS_ASSERTION).get(0);

        Optional<Assertion> cycle3Assertion = assertionMap.getOrDefault(AssertionType.CYCLE_3_ASSERTION, emptyList())
                .stream()
                .findFirst();

        return new AssertionData(mdsAssertion.getIssuer().getValue(),
                authnContextFactory.authnContextForLevelOfAssurance(levelOfAssurance),
                getCycle3Data(cycle3Assertion),
                matchingDatasetUnmarshaller.fromAssertion(mdsAssertion));
    }

    private AssertionType classifyAssertion(Assertion assertion) {
        if (!assertion.getAuthnStatements().isEmpty()) {
            return AssertionType.AUTHN_ASSERTION;
        } else if (assertion.getIssuer().getValue().equals(hubEntityId)) {
            return AssertionType.CYCLE_3_ASSERTION;
        }
        return AssertionType.MDS_ASSERTION;
    }
}
