package uk.gov.ida.matchingserviceadapter;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.setup.Environment;
import org.joda.time.Duration;
import org.opensaml.saml.saml2.core.AttributeQuery;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.EntitiesDescriptor;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Element;
import uk.gov.ida.common.shared.configuration.DeserializablePublicKeyConfiguration;
import uk.gov.ida.common.shared.configuration.PublicEncryptionKeyConfiguration;
import uk.gov.ida.common.shared.security.Certificate;
import uk.gov.ida.common.shared.security.IdGenerator;
import uk.gov.ida.common.shared.security.PublicKeyFactory;
import uk.gov.ida.common.shared.security.PublicKeyFileInputStreamFactory;
import uk.gov.ida.common.shared.security.PublicKeyInputStreamFactory;
import uk.gov.ida.common.shared.security.X509CertificateFactory;
import uk.gov.ida.jerseyclient.ErrorHandlingClient;
import uk.gov.ida.jerseyclient.JsonClient;
import uk.gov.ida.jerseyclient.JsonResponseProcessor;
import uk.gov.ida.matchingserviceadapter.configuration.AssertionLifetimeConfiguration;
import uk.gov.ida.matchingserviceadapter.configuration.CertificateStore;
import uk.gov.ida.matchingserviceadapter.configuration.KeyPairConfiguration;
import uk.gov.ida.matchingserviceadapter.domain.HealthCheckResponseFromMatchingService;
import uk.gov.ida.matchingserviceadapter.domain.OutboundResponseFromMatchingService;
import uk.gov.ida.matchingserviceadapter.domain.OutboundResponseFromUnknownUserCreationService;
import uk.gov.ida.matchingserviceadapter.domain.UserAccountCreationAttributeExtractor;
import uk.gov.ida.matchingserviceadapter.exceptions.ExceptionResponseFactory;
import uk.gov.ida.matchingserviceadapter.mappers.MatchingDatasetToMatchingDatasetDtoMapper;
import uk.gov.ida.matchingserviceadapter.mappers.MatchingServiceRequestDtoMapper;
import uk.gov.ida.matchingserviceadapter.mappers.MatchingServiceResponseDtoToOutboundResponseFromMatchingServiceMapper;
import uk.gov.ida.matchingserviceadapter.proxies.MatchingServiceProxy;
import uk.gov.ida.matchingserviceadapter.proxies.MatchingServiceProxyImpl;
import uk.gov.ida.matchingserviceadapter.repositories.MatchingServiceAdapterMetadataRepository;
import uk.gov.ida.matchingserviceadapter.rest.soap.SoapMessageManager;
import uk.gov.ida.matchingserviceadapter.saml.UserIdHashFactory;
import uk.gov.ida.matchingserviceadapter.saml.api.MsaTransformersFactory;
import uk.gov.ida.matchingserviceadapter.services.AttributeQueryService;
import uk.gov.ida.matchingserviceadapter.services.EidasAssertionService;
import uk.gov.ida.matchingserviceadapter.services.MatchingResponseGenerator;
import uk.gov.ida.matchingserviceadapter.services.UnknownUserResponseGenerator;
import uk.gov.ida.matchingserviceadapter.services.VerifyAssertionService;
import uk.gov.ida.matchingserviceadapter.validators.AttributeQuerySignatureValidator;
import uk.gov.ida.matchingserviceadapter.validators.AudienceRestrictionValidator;
import uk.gov.ida.matchingserviceadapter.validators.ConditionsValidator;
import uk.gov.ida.matchingserviceadapter.validators.DateTimeComparator;
import uk.gov.ida.matchingserviceadapter.validators.InstantValidator;
import uk.gov.ida.matchingserviceadapter.validators.SubjectValidator;
import uk.gov.ida.matchingserviceadapter.validators.TimeRestrictionValidator;
import uk.gov.ida.saml.core.OpenSamlXmlObjectFactory;
import uk.gov.ida.saml.core.api.CoreTransformersFactory;
import uk.gov.ida.saml.core.domain.AddressFactory;
import uk.gov.ida.saml.core.transformers.EidasMatchingDatasetUnmarshaller;
import uk.gov.ida.saml.core.transformers.VerifyMatchingDatasetUnmarshaller;
import uk.gov.ida.saml.core.transformers.inbound.Cycle3DatasetFactory;
import uk.gov.ida.saml.deserializers.ElementToOpenSamlXMLObjectTransformer;
import uk.gov.ida.saml.metadata.DisabledMetadataResolverRepository;
import uk.gov.ida.saml.metadata.EidasMetadataConfiguration;
import uk.gov.ida.saml.metadata.EidasMetadataResolverRepository;
import uk.gov.ida.saml.metadata.EidasTrustAnchorHealthCheck;
import uk.gov.ida.saml.metadata.EidasTrustAnchorResolver;
import uk.gov.ida.saml.metadata.ExpiredCertificateMetadataFilter;
import uk.gov.ida.saml.metadata.MetadataConfiguration;
import uk.gov.ida.saml.metadata.MetadataResolverConfigBuilder;
import uk.gov.ida.saml.metadata.MetadataResolverRepository;
import uk.gov.ida.saml.metadata.factories.DropwizardMetadataResolverFactory;
import uk.gov.ida.saml.metadata.factories.MetadataSignatureTrustEngineFactory;
import uk.gov.ida.saml.metadata.transformers.KeyDescriptorsUnmarshaller;
import uk.gov.ida.saml.security.AssertionDecrypter;
import uk.gov.ida.saml.security.DecrypterFactory;
import uk.gov.ida.saml.security.EntityToEncryptForLocator;
import uk.gov.ida.saml.security.IdaKeyStore;
import uk.gov.ida.saml.security.IdaKeyStoreCredentialRetriever;
import uk.gov.ida.saml.security.MetadataBackedEncryptionCredentialResolver;
import uk.gov.ida.saml.security.MetadataBackedSignatureValidator;
import uk.gov.ida.saml.security.SamlAssertionsSignatureValidator;
import uk.gov.ida.saml.security.SamlMessageSignatureValidator;
import uk.gov.ida.saml.security.validators.encryptedelementtype.EncryptionAlgorithmValidator;
import uk.gov.ida.shared.utils.manifest.ManifestReader;
import uk.gov.ida.truststore.KeyStoreLoader;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import java.security.KeyPair;
import java.util.List;
import java.util.Timer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.ida.common.shared.security.Certificate.KeyUse.Encryption;
import static uk.gov.ida.common.shared.security.Certificate.KeyUse.Signing;

class MatchingServiceAdapterModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SoapMessageManager.class).toInstance(new SoapMessageManager());
        bind(X509CertificateFactory.class);
        bind(KeyStoreLoader.class);
        bind(IdGenerator.class);
        bind(ExpiredCertificateMetadataFilter.class);
        bind(ExceptionResponseFactory.class);
        bind(MatchingServiceResponseDtoToOutboundResponseFromMatchingServiceMapper.class);
        bind(MatchingServiceAdapterMetadataRepository.class);
        bind(IdGenerator.class);
        bind(UserAccountCreationAttributeExtractor.class);
        bind(UnknownUserResponseGenerator.class);

        bind(AttributeQuerySignatureValidator.class);
        bind(InstantValidator.class);
        bind(TimeRestrictionValidator.class);
        bind(SubjectValidator.class);
        bind(AudienceRestrictionValidator.class);
        bind(ConditionsValidator.class);
        //bind(AttributeQueryService.class);

        bind(PublicKeyInputStreamFactory.class).to(PublicKeyFileInputStreamFactory.class).in(Singleton.class);
        bind(AssertionLifetimeConfiguration.class).to(MatchingServiceAdapterConfiguration.class).in(Singleton.class);
        bind(MatchingServiceProxy.class).to(MatchingServiceProxyImpl.class).in(Singleton.class);
        bind(ManifestReader.class).toInstance(new ManifestReader());
        bind(MatchingDatasetToMatchingDatasetDtoMapper.class).toInstance(new MatchingDatasetToMatchingDatasetDtoMapper());
    }

    @Provides
    @Singleton
    public DateTimeComparator getDateTimeComparator(MatchingServiceAdapterConfiguration configuration) {
        return new DateTimeComparator(Duration.standardSeconds(configuration.getClockSkew()));
    }

    @Provides
    @Singleton
    public SamlAssertionsSignatureValidator getSamlAssertionsSignatureValidator(SamlMessageSignatureValidator signatureValidator) {
        return new SamlAssertionsSignatureValidator(signatureValidator);
    }

    @Provides
    @Singleton
    public SamlMessageSignatureValidator getSamlMessageSignatureValidator(MetadataBackedSignatureValidator metadataBackedSignatureValidator) {
        return new SamlMessageSignatureValidator(metadataBackedSignatureValidator);
    }

    @Provides
    @Singleton
    public AddressFactory getAddressFactory() {
        return new AddressFactory();
    }

    @Provides
    @Singleton
    public EidasMatchingDatasetUnmarshaller getEidasMatchingDatasetUnmarshaller() {
        return new EidasMatchingDatasetUnmarshaller();
    }

    @Provides
    @Singleton
    public VerifyMatchingDatasetUnmarshaller getVerifyMatchingDatasetUnmarshaller(AddressFactory addressFactory) {
        return new VerifyMatchingDatasetUnmarshaller(addressFactory);
    }

    @Provides
    @Singleton
    public MetadataBackedEncryptionCredentialResolver hubEncryptionCredentialResolver(MetadataCredentialResolver metadataCredentialResolver) {
        return new MetadataBackedEncryptionCredentialResolver(metadataCredentialResolver, SPSSODescriptor.DEFAULT_ELEMENT_NAME);
    }

    @Provides
    @Singleton
    public IdaKeyStoreCredentialRetriever getIdaKeyStoreCredentialRetriever(IdaKeyStore keyStore) {
        return new IdaKeyStoreCredentialRetriever(keyStore);
    }

    @Provides
    @Singleton
    public UserIdHashFactory getUserIdHashFactory(MatchingServiceAdapterConfiguration configuration) {
        return new UserIdHashFactory(configuration.getEntityId());
    }

    @Provides
    @Singleton
    public Cycle3DatasetFactory getCycle3DatasetFactory() {
        return new Cycle3DatasetFactory();
    }

    @Provides
    @Singleton
    public MatchingServiceRequestDtoMapper getInboundMatchingServiceRequestToMatchingServiceRequestDtoMapper(
            MatchingDatasetToMatchingDatasetDtoMapper matchingDatasetToMatchingDatasetDtoMapper,
            MatchingServiceAdapterConfiguration configuration
    ) {
        return new MatchingServiceRequestDtoMapper(matchingDatasetToMatchingDatasetDtoMapper, configuration.isEidasEnabled());
    }

    @Provides
    @Singleton
    public VerifyAssertionService getVerifyAssertionService(
            InstantValidator instantValidator,
            SubjectValidator subjectValidator,
            ConditionsValidator conditionsValidator,
            SamlAssertionsSignatureValidator signatureValidator,
            Cycle3DatasetFactory cycle3DatasetFactory,
            VerifyMatchingDatasetUnmarshaller matchingDatasetUnmarshaller,
            @Named("HubEntityId") String hubEntityId
    ) {
        return new VerifyAssertionService(instantValidator, subjectValidator, conditionsValidator, signatureValidator, cycle3DatasetFactory, hubEntityId, matchingDatasetUnmarshaller);
    }

    @Provides
    @Singleton
    public AttributeQueryService getAttributeQueryService(
            AttributeQuerySignatureValidator attributeQuerySignatureValidator,
            InstantValidator instantValidator,
            VerifyAssertionService verifyAssertionService,
            EidasAssertionService eidasAssertionService,
            UserIdHashFactory userIdHashFactory,
            @Named("HubEntityId") String hubEntityId
    ) {
        return new AttributeQueryService(attributeQuerySignatureValidator, instantValidator, verifyAssertionService, eidasAssertionService, userIdHashFactory, hubEntityId);
    }

    @Provides
    @Singleton
    public EidasAssertionService getCountryAssertionService(
            InstantValidator instantValidator,
            SubjectValidator subjectValidator,
            ConditionsValidator conditionsValidator,
            SamlAssertionsSignatureValidator hubSignatureValidator,
            Cycle3DatasetFactory cycle3DatasetFactory,
            MetadataResolverRepository eidasMetadataRepository,
            EidasMatchingDatasetUnmarshaller matchingDatasetUnmarshaller,
            @Named("HubConnectorEntityId") String hubConnectorEntityId,
            @Named("HubEntityId") String hubEntityId
    ) {
        return new EidasAssertionService(instantValidator,
                subjectValidator,
                conditionsValidator,
                hubSignatureValidator,
                cycle3DatasetFactory,
                eidasMetadataRepository,
                hubConnectorEntityId,
                hubEntityId,
                matchingDatasetUnmarshaller);
    }

    @Provides
    @Singleton
    public MatchingResponseGenerator getResponseGenerator(
            SoapMessageManager soapMessageManager,
            Function<HealthCheckResponseFromMatchingService, Element> healthCheckResponseTransformer,
            ManifestReader manifestReader,
            Function<OutboundResponseFromMatchingService, Element> responseElementTransformer,
            MatchingServiceAdapterConfiguration configuration
    ) {
        return new MatchingResponseGenerator(soapMessageManager,
                responseElementTransformer,
                healthCheckResponseTransformer,
                manifestReader,
                configuration.getEntityId());
    }

    @Provides
    @Singleton
    public MetadataBackedSignatureValidator verifyMetadataSignatureValidator(ExplicitKeySignatureTrustEngine explicitKeySignatureTrustEngine) {
        return MetadataBackedSignatureValidator.withoutCertificateChainValidation(explicitKeySignatureTrustEngine);
    }

    @Provides
    @Singleton
    @Named("HubEntityId")
    public String getHubEntityId(MatchingServiceAdapterConfiguration configuration) {
        return configuration.getHubEntityId();
    }

    @Provides
    @Singleton
    @Named("HubConnectorEntityId")
    public String getHubConnectorEntityId(MatchingServiceAdapterConfiguration configuration) {
        return configuration.isEidasEnabled() ? configuration.getEuropeanIdentity().getHubConnectorEntityId() : "";
    }

    @Provides
    @Singleton
    @Named("HubFederationId")
    public String getHubFederationId(MatchingServiceAdapterConfiguration configuration) {
        return configuration.getMetadataConfiguration().getHubFederationId();
    }

    @Provides
    public EntityToEncryptForLocator getEntityToEncryptFor(@Named("HubEntityId") String hubEntityId) {
        return requestId -> hubEntityId;
    }

    @Provides
    @Singleton
    public OpenSamlXmlObjectFactory getOpenSamlXmlObjectFactory() {
        return new OpenSamlXmlObjectFactory();
    }

    @Provides
    @Singleton
    public PublicKeyFactory getPublicKeyFactory(X509CertificateFactory x509CertificateFactory) {
        return new PublicKeyFactory(x509CertificateFactory);
    }

    @Provides
    @Singleton
    @Named("MatchingServiceClient")
    public JsonClient getMatchingServiceClient(Environment environment, MatchingServiceAdapterConfiguration configuration) {
        Client matchingServiceClient = new JerseyClientBuilder(environment).using(configuration.getMatchingServiceClientConfiguration()).build("MatchingServiceClient");
        return new JsonClient(new ErrorHandlingClient(matchingServiceClient), new JsonResponseProcessor(environment.getObjectMapper()));
    }

    @Provides
    @Singleton
    @PublicEncryptionKeyConfiguration
    public DeserializablePublicKeyConfiguration getPublicEncryptionKeyConfiguration(MatchingServiceAdapterConfiguration configuration) {
        return configuration.getEncryptionKeys().get(0).getPublicKey();
    }

    @Provides
    @Singleton
    public CertificateStore getCertificateStore(MatchingServiceAdapterConfiguration configuration) {
        List<Certificate> publicSigningCertificates = configuration.getSigningKeys().stream()
                .map(KeyPairConfiguration::getPublicKey)
                .map(key -> cert(key.getName(), key.getCert(), Signing))
                .collect(Collectors.toList());

        List<Certificate> publicEncryptionCertificates = Stream.of(configuration.getEncryptionKeys().get(0).getPublicKey())
                .map(key -> cert(key.getName(), key.getCert(), Encryption))
                .collect(Collectors.toList());

        return new CertificateStore(
                publicEncryptionCertificates,
                publicSigningCertificates);
    }

    private Certificate cert(String keyName, String cert, Certificate.KeyUse keyUse) {
        String certBody = cert
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace(" ", "");
        return new Certificate(keyName, certBody, keyUse);
    }

    @Provides
    @Singleton
    public Function<HealthCheckResponseFromMatchingService, Element> getHealthcheckResponseFromMatchingServiceToElementTransformer(
            MetadataBackedEncryptionCredentialResolver encryptionCredentialResolver,
            IdaKeyStore idaKeyStore,
            EntityToEncryptForLocator entityToEncryptForLocator,
            MatchingServiceAdapterConfiguration configuration
    ) {
        return new MsaTransformersFactory().getHealthcheckResponseFromMatchingServiceToElementTransformer(
                encryptionCredentialResolver,
                idaKeyStore,
                entityToEncryptForLocator,
                configuration
        );
    }

    @Provides
    @Singleton
    public Function<OutboundResponseFromMatchingService, Element> getOutboundResponseFromMatchingServiceToElementTransformer(
            MetadataBackedEncryptionCredentialResolver encryptionCredentialResolver,
            IdaKeyStore idaKeyStore,
            EntityToEncryptForLocator entityToEncryptForLocator,
            MatchingServiceAdapterConfiguration configuration
    ) {
        return new MsaTransformersFactory().getOutboundResponseFromMatchingServiceToElementTransformer(
                encryptionCredentialResolver,
                idaKeyStore,
                entityToEncryptForLocator,
                configuration
        );
    }

    @Provides
    @Singleton
    public Function<OutboundResponseFromUnknownUserCreationService, Element> getOutboundResponseFromUnknownUserCreationServiceToElementTransformer(
            MetadataBackedEncryptionCredentialResolver encryptionCredentialResolver,
            IdaKeyStore idaKeyStore,
            EntityToEncryptForLocator entityToEncryptForLocator,
            MatchingServiceAdapterConfiguration configuration
    ) {
        return new MsaTransformersFactory().getOutboundResponseFromUnknownUserCreationServiceToElementTransformer(
                encryptionCredentialResolver,
                idaKeyStore,
                entityToEncryptForLocator,
                configuration
        );
    }

    @Provides
    @Singleton
    public ElementToOpenSamlXMLObjectTransformer<AttributeQuery> getAttributeQueryTransformer() {
        return new CoreTransformersFactory().getElementToOpenSamlXmlObjectTransformer();
    }

    @Provides
    public AssertionDecrypter getAssertionDecrypter(IdaKeyStore eidasKeystore) {
        IdaKeyStoreCredentialRetriever idaKeyStoreCredentialRetriever = new IdaKeyStoreCredentialRetriever(eidasKeystore);
        Decrypter decrypter = new DecrypterFactory().createDecrypter(idaKeyStoreCredentialRetriever.getDecryptingCredentials());
        return new AssertionDecrypter(new EncryptionAlgorithmValidator(), decrypter);
    }

    @Provides
    @Singleton
    public ElementToOpenSamlXMLObjectTransformer<EntityDescriptor> getMetadataForSpTransformer() {
        return new CoreTransformersFactory().getElementToOpenSamlXmlObjectTransformer();
    }

    @Provides
    @Singleton
    public KeyDescriptorsUnmarshaller getCertificatesToKeyDescriptorsTransformer() {
        return new CoreTransformersFactory().getCertificatesToKeyDescriptorsTransformer();
    }

    @Provides
    @Singleton
    public Function<EntitiesDescriptor, Element> getEntityDescriptorToElementTransformer() {
        return new CoreTransformersFactory().getXmlObjectToElementTransformer();
    }

    @Provides
    @Singleton
    public IdaKeyStore getIdaKeyStore(MatchingServiceAdapterConfiguration configuration) {
        List<KeyPair> encryptionKeyPairs = configuration.getEncryptionKeys().stream()
                .map(pair -> new KeyPair(pair.getPublicKey().getPublicKey(), pair.getPrivateKey().getPrivateKey()))
                .collect(Collectors.toList());

        KeyPair signingKeyPair = new KeyPair(
                configuration.getSigningKeys().get(0).getPublicKey().getPublicKey(),
                configuration.getSigningKeys().get(0).getPrivateKey().getPrivateKey()
        );

        X509CertificateFactory x509CertificateFactory = new X509CertificateFactory();
        return new IdaKeyStore(x509CertificateFactory.createCertificate(configuration.getSigningKeys().get(0).getPublicKey().getCert()), signingKeyPair, encryptionKeyPairs);
    }

    @Provides
    @Singleton
    public MetadataConfiguration metadataConfiguration(MatchingServiceAdapterConfiguration msaConfiguration) {
        return msaConfiguration.getMetadataConfiguration();
    }

    @Provides
    @Singleton
    public Client getEidasMetadataJerseyClient(Environment environment, MatchingServiceAdapterConfiguration configuration) {
        if (!configuration.isEidasEnabled()) return null;

        EidasMetadataConfiguration metadataConfiguration = configuration.getEuropeanIdentity().getAggregatedMetadata();
        return new JerseyClientBuilder(environment)
                .using(metadataConfiguration.getJerseyClientConfiguration())
                .build(metadataConfiguration.getJerseyClientName());
    }

    @Provides
    @Singleton
    public EidasTrustAnchorResolver getEidasTrustAnchorResolver(MatchingServiceAdapterConfiguration configuration, @Nullable Client client) {
        if (!configuration.isEidasEnabled()) return null;

        EidasMetadataConfiguration metadataConfiguration = configuration.getEuropeanIdentity().getAggregatedMetadata();
        return new EidasTrustAnchorResolver(metadataConfiguration.getTrustAnchorUri(), client, metadataConfiguration.getTrustStore());
    }

    @Provides
    @Singleton
    public MetadataResolverRepository getEidasMetadataResolverRepository(
            Environment environment,
            MatchingServiceAdapterConfiguration configuration,
            @Nullable EidasTrustAnchorResolver trustAnchorResolver,
            @Nullable Client client
    ) {
        if (!configuration.isEidasEnabled()) return new DisabledMetadataResolverRepository();

        EidasMetadataResolverRepository resolverRepository = new EidasMetadataResolverRepository(
                trustAnchorResolver,
                configuration.getEuropeanIdentity().getAggregatedMetadata(),
                new DropwizardMetadataResolverFactory(),
                new Timer(),
                new MetadataSignatureTrustEngineFactory(),
                new MetadataResolverConfigBuilder(),
                client
        );

        environment.healthChecks().register("TrustAnchorHealthCheck", new EidasTrustAnchorHealthCheck(resolverRepository));
        return resolverRepository;
    }
}
