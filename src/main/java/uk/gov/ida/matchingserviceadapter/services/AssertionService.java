package uk.gov.ida.matchingserviceadapter.services;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import uk.gov.ida.matchingserviceadapter.domain.AssertionData;
import uk.gov.ida.matchingserviceadapter.validators.ConditionsValidator;
import uk.gov.ida.matchingserviceadapter.validators.InstantValidator;
import uk.gov.ida.matchingserviceadapter.validators.SubjectValidator;
import uk.gov.ida.saml.core.domain.Cycle3Dataset;
import uk.gov.ida.saml.core.transformers.inbound.Cycle3DatasetFactory;
import uk.gov.ida.saml.security.SamlAssertionsSignatureValidator;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;

public abstract class AssertionService {
    protected final InstantValidator instantValidator;
    protected final SubjectValidator subjectValidator;
    protected final ConditionsValidator conditionsValidator;
    private final SamlAssertionsSignatureValidator hubSignatureValidator;
    private final Cycle3DatasetFactory cycle3DatasetFactory;

    protected AssertionService(InstantValidator instantValidator,
                               SubjectValidator subjectValidator,
                               ConditionsValidator conditionsValidator,
                               SamlAssertionsSignatureValidator hubSignatureValidator,
                               Cycle3DatasetFactory cycle3DatasetFactory) {
        this.instantValidator = instantValidator;
        this.subjectValidator = subjectValidator;
        this.conditionsValidator = conditionsValidator;
        this.hubSignatureValidator = hubSignatureValidator;
        this.cycle3DatasetFactory = cycle3DatasetFactory;
    }

    abstract void validate(String requestId, List<Assertion> assertions);

    abstract AssertionData translate(List<Assertion> assertions);

    protected void validateHubAssertion(Assertion assertion,
                                        String expectedInResponseTo,
                                        String hubEntityId,
                                        QName role) {
        hubSignatureValidator.validate(singletonList(assertion), role);
        instantValidator.validate(assertion.getIssueInstant(), "Hub Assertion IssueInstant");
        subjectValidator.validate(assertion.getSubject(), expectedInResponseTo);
        if (assertion.getConditions() != null) {
            conditionsValidator.validate(assertion.getConditions(), hubEntityId);
        }
    }

    protected void validateCycle3Assertion(Assertion assertion, String requestId, String hubEntityId) {
        validateHubAssertion(assertion, requestId, hubEntityId, SPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    protected Optional<Cycle3Dataset> getCycle3Data(Optional<Assertion> assertion) {
        return assertion.map(cycle3DatasetFactory::createCycle3DataSet);
    }
}
