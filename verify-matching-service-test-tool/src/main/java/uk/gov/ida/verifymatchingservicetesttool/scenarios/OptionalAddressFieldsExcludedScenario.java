package uk.gov.ida.verifymatchingservicetesttool.scenarios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.ida.verifymatchingservicetesttool.configurations.ApplicationConfiguration;
import uk.gov.ida.verifymatchingservicetesttool.resolvers.ApplicationConfigurationResolver;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@ExtendWith(ApplicationConfigurationResolver.class)
public class OptionalAddressFieldsExcludedScenario extends ScenarioBase {

    public OptionalAddressFieldsExcludedScenario(ApplicationConfiguration configuration) {
        super(configuration);
    }

    @Test
    @DisplayName("Simple request with address missing optional fields")
    public void runCase() throws Exception {
        Response response = client.target(configuration.getLocalMatchingServiceMatchUrl())
            .request(APPLICATION_JSON)
            .post(Entity.json(fileUtils.readFromResources("simple-case-excluding-optional-address-fields.json")));

        validateMatchNoMatch(response);
    }
}
