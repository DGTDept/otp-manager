package io.mosip.kernel.otpmanager.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.otpmanager.spi.OtpGenerator;
import io.mosip.kernel.otpmanager.constant.OtpStatusConstants;
import io.mosip.kernel.otpmanager.dto.OtpGeneratorRequestDto;
import io.mosip.kernel.otpmanager.dto.OtpGeneratorResponseDto;
import io.mosip.kernel.otpmanager.entity.OtpEntity;
import io.mosip.kernel.otpmanager.repository.OtpRepository;
import io.mosip.kernel.otpmanager.util.OtpManagerUtils;
import io.mosip.kernel.otpmanager.util.OtpProvider;

/**
 * This class provides the implementation for the methods of OtpGeneratorService
 * interface.
 *
 * @author Ritesh Sinha
 * @since 1.0.0
 *
 */
@RefreshScope
@Service
public class OtpGeneratorServiceImpl implements OtpGenerator<OtpGeneratorRequestDto, OtpGeneratorResponseDto> {
	/**
	 * The reference that autowires OtpRepository class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OtpGeneratorServiceImpl.class);
	@Autowired
	private OtpRepository otpRepository;

	@Autowired
	private OtpProvider otpProvider;

	@Value("${mosip.kernel.otp.key-freeze-time}")
	String keyFreezeTime;

	@Value("${mosip.kernel.otp.default-length}")
	int otpLength;

	@Value("${mosip.kernel.otp.mac-algorithm}")
	String macAlgorithm;

	@Value("${javax.persistence.jdbc.url}")
	String jdbcUrl;
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.kernel.otpmanagerservice.service.OtpGeneratorService#getOtp(org.
	 * mosip.kernel.otpmanagerservice.dto.OtpGeneratorRequestDto)
	 */
	@Override
	public OtpGeneratorResponseDto getOtp(OtpGeneratorRequestDto otpDto) {

		String generatedOtp;

		/*
		 * Creating object to return the generation response.
		 */
		OtpGeneratorResponseDto response = new OtpGeneratorResponseDto();
		/*
		 * Checking whether the key exists in the repository.
		 */
		String refIdHash = OtpManagerUtils.getHash(otpDto.getKey());
		Optional<OtpEntity> entityOpt = otpRepository.findFirstByRefIdOrderByGeneratedDtimesDesc(refIdHash);
		if (entityOpt.isPresent() && (entityOpt.get().getStatusCode().equals(OtpStatusConstants.KEY_FREEZED.getProperty()))
				&& (OtpManagerUtils.timeDifferenceInSeconds(entityOpt.get().getUpdatedDtimes(),
						LocalDateTime.now(ZoneId.of("UTC"))) <= Integer.parseInt(keyFreezeTime))) {
			response.setOtp(OtpStatusConstants.SET_AS_NULL_IN_STRING.getProperty());
			response.setStatus(OtpStatusConstants.BLOCKED_USER.getProperty());
		} else {
			generatedOtp = otpProvider.computeOtp(otpDto.getKey(), otpLength, macAlgorithm);
			
			if (entityOpt.isPresent()) {
				otpRepository.delete(entityOpt.get());
			}
			
			OtpEntity otp = new OtpEntity();
			otp.setId(OtpManagerUtils.getKeyOtpHash(otpDto.getKey(), generatedOtp));
			otp.setRefId(refIdHash);
			otp.setValidationRetryCount(0);
			otpRepository.save(otp);
			response.setOtp(generatedOtp);
			response.setStatus(OtpStatusConstants.GENERATION_SUCCESSFUL.getProperty());
		}
		return response;
	}
}
