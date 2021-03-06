package uk.nhs.nhsx.highriskpostcodesupload;

import uk.nhs.nhsx.activationsubmission.persist.Environment;
import uk.nhs.nhsx.core.ContentTypes;
import uk.nhs.nhsx.core.HttpResponses;
import uk.nhs.nhsx.core.SystemClock;
import uk.nhs.nhsx.core.auth.ApiName;
import uk.nhs.nhsx.core.auth.Authenticator;
import uk.nhs.nhsx.core.aws.cloudfront.AwsCloudFront;
import uk.nhs.nhsx.core.aws.cloudfront.AwsCloudFrontClient;
import uk.nhs.nhsx.core.aws.s3.*;
import uk.nhs.nhsx.core.csvupload.CsvToJsonParser;
import uk.nhs.nhsx.core.csvupload.CsvUploadService;
import uk.nhs.nhsx.core.exceptions.ApiResponseException;
import uk.nhs.nhsx.core.exceptions.HttpStatusCode;
import uk.nhs.nhsx.core.routing.Routing;
import uk.nhs.nhsx.core.routing.RoutingHandler;
import uk.nhs.nhsx.core.signature.DatedSigner;

import java.time.Instant;
import java.util.function.Supplier;

import static uk.nhs.nhsx.core.StandardSigning.datedSigner;
import static uk.nhs.nhsx.core.auth.StandardAuthentication.awsAuthentication;
import static uk.nhs.nhsx.core.routing.Routing.*;
import static uk.nhs.nhsx.core.routing.StandardHandlers.withoutSignedResponses;

/**
 * High risk post district upload lambda
 * <p>
 * doc/design/api-contracts/risky-post-district-upload.md
 * <p>
 * Sample:
 * <pre>
 * cat << EOF > riskypostcodes.csv
 * # postal_district_code, risk_indicator
 * "CODE1", "H"
 * EOF
 *
 * curl -v -H "Content-Type: text/csv" -H "Authorization: Bearer [token]" --data-binary @riskypostcodes.csv https://vpgd3ah06a.execute-api.eu-west-2.amazonaws.com/upload/high-risk-postal-districts
 * 200
 *
 * curl https://distribution-147902.dev.svc-test-trace.nhs.uk/distribution/risky-post-districts
 * {"postDistricts":{"CODE1":"H"}}
 * </pre>
 */
public class Handler extends RoutingHandler {

    private static final String DISTRIBUTION_ID = "DISTRIBUTION_ID";
    private static final String DISTRIBUTION_INVALIDATION_PATTERN = "DISTRIBUTION_INVALIDATION_PATTERN";
    private static final String DISTRIBUTION_OBJ_KEY_NAME = "distribution/risky-post-districts";
    private static final String RAW_OBJ_KEY_NAME = "raw/risky-post-districts";
    private static final String BUCKET_NAME = "BUCKET_NAME";

    private final Routing.Handler handler;

    public Handler() {
        this(Environment.fromSystem(), SystemClock.CLOCK);
    }

    public Handler(Environment environment, Supplier<Instant> clock) {
        this(
            environment,
            awsAuthentication(ApiName.HighRiskPostCodeUpload),
            datedSigner(clock, environment),
            new AwsS3Client(),
            new AwsCloudFrontClient(),
            new RiskyPostCodesCsvParser()
        );
    }

    public Handler(
        Environment environment,
        Authenticator authenticator,
        DatedSigner signer,
        S3Storage s3Storage,
        AwsCloudFront awsCloudFront,
        CsvToJsonParser parser) {
        CsvUploadService service = new CsvUploadService(
            BucketName.of(environment.access.required(BUCKET_NAME)),
            ObjectKey.of(DISTRIBUTION_OBJ_KEY_NAME),
            ObjectKey.of(RAW_OBJ_KEY_NAME),
            signer,
            s3Storage,
            awsCloudFront,
            environment.access.required(DISTRIBUTION_ID),
            environment.access.required(DISTRIBUTION_INVALIDATION_PATTERN),
            parser);
        handler = withoutSignedResponses(
            environment, 
            authenticator,
            routes(
                path(Method.POST, "/upload/high-risk-postal-districts", (r) -> {
                    if (!ContentTypes.isTextCsv(r))
                        throw new ApiResponseException(HttpStatusCode.UNPROCESSABLE_ENTITY_422, "Content type is not text/csv");

                    service.upload(r.getBody());
                    return HttpResponses.accepted("successfully uploaded");
                }),
                path(Routing.Method.POST, "/upload/high-risk-postal-districts/health", (r) ->
                    HttpResponses.ok()
                ))
        );

    }

    @Override
    public Routing.Handler handler() {
        return handler;
    }

}
