package uk.gov.ida.matchingserviceadapter.rest.matchingservice;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

// CAUTION!!! CHANGES TO THIS CLASS WILL IMPACT MSA USERS
@JsonSerialize(include= JsonSerialize.Inclusion.NON_NULL)
public class VerifyMatchingDatasetDto extends MatchingDatasetDto {

    private List<VerifyAddressDto> addresses = new ArrayList<>();

    @SuppressWarnings("unused") // needed for JAXB
    private VerifyMatchingDatasetDto() {
        super();
    }

    public VerifyMatchingDatasetDto(
            Optional<SimpleMdsValueDto<String>> firstName,
            Optional<SimpleMdsValueDto<String>> middleNames,
            List<SimpleMdsValueDto<String>> surnames,
            Optional<SimpleMdsValueDto<GenderDto>> gender,
            Optional<SimpleMdsValueDto<LocalDate>> dateOfBirth,
            List<VerifyAddressDto> addresses) {
        super(firstName, middleNames, surnames, gender, dateOfBirth);

        this.addresses = addresses;
    }

    public List<VerifyAddressDto> getAddresses() {
        return addresses;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}