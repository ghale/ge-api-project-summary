package com.gradle.enterprise.summary;


import com.gradle.enterprise.api.GradleEnterpriseApi;
import com.gradle.enterprise.api.client.ApiClient;
import com.gradle.enterprise.api.model.Build;
import com.gradle.enterprise.summary.accumulator.BuildAccumulator;
import com.gradle.enterprise.summary.accumulator.WorkUnitAccumulator;
import com.gradle.enterprise.summary.accumulator.gradle.GradleSummaryAccumulator;
import com.gradle.enterprise.summary.accumulator.maven.MavenSummaryAccumulator;
import com.gradle.enterprise.summary.metrics.ErrorAccumulator;
import com.gradle.enterprise.summary.metrics.MetricsAccumulator;
import com.gradle.enterprise.summary.processor.BuildsFetcher;
import com.gradle.enterprise.summary.processor.ConcurrentBuildProcessor;
import com.gradle.enterprise.summary.processor.SerialBuildProcessor;
import com.gradle.enterprise.summary.writer.ErrorsWriter;
import com.gradle.enterprise.summary.writer.SummaryWriter;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(
    name = "gradle-enterprise-project-summary",
    description = "A compact utility that gathers project metrics using Gradle Enterprise API",
    synopsisHeading = "%n@|bold Usage:|@ ",
    optionListHeading = "%n@|bold Options:|@%n",
    commandListHeading = "%n@|bold Commands:|@%n",
    parameterListHeading = "%n@|bold Parameters:|@%n",
    descriptionHeading = "%n",
    synopsisSubcommandLabel = "COMMAND",
    usageHelpAutoWidth = true,
    usageHelpWidth = 120
)
public class SummaryMain implements Callable<Integer> {
    private static final Instant START_TIME = InstantUtils.nowUTC();

    @CommandLine.Option(
        names = "--server-url",
        description = "The address of the Gradle Enterprise server",
        required = true,
        order = 0
    )
    String serverUrl;

    @CommandLine.Option(
        names = "--access-key-file",
        description = "The path to the file containing the access key",
        required = true,
        order = 1
    )
    String accessKeyFile;

    @CommandLine.Option(
        names = "--project-name",
        description = "The name of the project to show the builds of (if omitted, all projects are evaluated)",
        order = 2
    )
    String projectName;

    @CommandLine.Option(
        names = "--days",
        description = "The total number of days to process from the past (if set will ignore hours)",
        order = 3
    )
    Integer days;

    @CommandLine.Option(
        names = "--hours",
        description = "The total number of hours to process from the past (if days set this will be ignored)",
        order = 4
    )
    Integer hours;

    @CommandLine.Option(
        names = "--work-units",
        description = "The number of tasks/goals to display for each project",
        order = 5,
        defaultValue = "10"
    )
    Integer nrOfWorkUnits;

    @CommandLine.Option(
        names = "--format-duration",
        description = "Formatting the durations by HH:mm:ss",
        order = 6
    )
    boolean shouldFormatDuration;

    @CommandLine.Option(
        names = "--concurrency",
        description = "The number of threads used for processing the builds",
        order = 7,
        defaultValue = "32"
    )
    int numberOfThreads;

    public static void main(String[] args) {
        System.setProperty("run.date", InstantUtils.toString(START_TIME));

        System.exit(new CommandLine(new SummaryMain()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        final var outputWriter = new SummaryWriter(shouldFormatDuration, getTransforms());
        final var gradleSummaryAccumulator = new GradleSummaryAccumulator();
        final var mavenSummaryAccumulator = new MavenSummaryAccumulator();
        final var errorsWriter = new ErrorsWriter();
        final var errorAccumulator = new ErrorAccumulator();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> writeOutput(outputWriter, gradleSummaryAccumulator, mavenSummaryAccumulator, errorsWriter, errorAccumulator)));

        var serverUrl = sanitizeServerUrl();
        var accessKey = Files.readString(Paths.get(accessKeyFile)).trim();
        var projectName = this.projectName == null || this.projectName.isBlank() ? null : this.projectName;

        var apiClient = configureApiClient(serverUrl, accessKey);
        var api = new GradleEnterpriseApi(apiClient);

        var buildsProcessor = new BuildsFetcher(api, errorAccumulator);

        Instant now = InstantUtils.nowUTC();
        var builds = buildsProcessor.fetchBuilds(getStartTime(now), now);

        var metricsAccumulator = new MetricsAccumulator(builds.size());
        metricsAccumulator.startProcessing();
        processBuilds(api, builds, gradleSummaryAccumulator, mavenSummaryAccumulator, metricsAccumulator, errorAccumulator, projectName);

        writeOutput(outputWriter, gradleSummaryAccumulator, mavenSummaryAccumulator, errorsWriter, errorAccumulator);

        return 0;
    }

    private void writeOutput(SummaryWriter outputWriter, GradleSummaryAccumulator gradleSummaryAccumulator, MavenSummaryAccumulator mavenSummaryAccumulator, ErrorsWriter errorsWriter, ErrorAccumulator errorAccumulator) {
        outputWriter.write(gradleSummaryAccumulator, START_TIME, "gradle");
        outputWriter.write(mavenSummaryAccumulator, START_TIME, "maven");

        if (!errorAccumulator.getErrors().isEmpty()) {
            errorsWriter.write(errorAccumulator, START_TIME);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<StreamTransform<?>> getTransforms() {
        List<StreamTransform<?>> transforms = new ArrayList<>();
        transforms.add(new StreamTransform<>(WorkUnitAccumulator.class) {
            @Override
            public Stream<WorkUnitAccumulator> transform(Stream stream) {
                return stream
                    .sorted((o1, o2) -> Long.compare(((WorkUnitAccumulator<?, ?>) o2).getTotalAvoidableTime(), ((WorkUnitAccumulator) o1).getTotalAvoidableTime()))
                    .limit(nrOfWorkUnits);
            }
        });

        transforms.add(new StreamTransform<>(BuildAccumulator.class) {
            @Override
            public Stream<BuildAccumulator> transform(Stream stream) {
                return stream
                    .sorted((o1, o2) -> Long.compare(((BuildAccumulator) o2).getTotalAvoidableTime(), ((BuildAccumulator) o1).getTotalAvoidableTime()));
            }
        });

        return transforms;
    }

    private void processBuilds(
        GradleEnterpriseApi api,
        Queue<Build> builds,
        GradleSummaryAccumulator gradleSummaryAccumulator,
        MavenSummaryAccumulator mavenSummaryAccumulator,
        MetricsAccumulator metricsAccumulator,
        ErrorAccumulator errorAccumulator,
        String projectName
    ) {
        ConcurrentBuildProcessor buildProcessor = new ConcurrentBuildProcessor(numberOfThreads,
            new SerialBuildProcessor(
                api, serverUrl, projectName, gradleSummaryAccumulator,
                mavenSummaryAccumulator, metricsAccumulator, errorAccumulator
            ),
            builds);

        buildProcessor.processBuilds();
    }

    private Instant getStartTime(Instant now) {
        if (hours != null) {
            return now.minus(Duration.ofHours(hours));
        } else {
            return now.minus(Duration.ofDays(Objects.requireNonNullElse(days, 1)));
        }
    }

    private String sanitizeServerUrl() {
        return serverUrl.endsWith("/")
            ? serverUrl.substring(0, serverUrl.length() - 1)
            : serverUrl;
    }

    private ApiClient configureApiClient(String serverUrl, String accessKey) {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri(serverUrl);
        apiClient.setRequestInterceptor(request -> request.setHeader("Authorization", "Bearer " + accessKey));
        return apiClient;
    }
}
