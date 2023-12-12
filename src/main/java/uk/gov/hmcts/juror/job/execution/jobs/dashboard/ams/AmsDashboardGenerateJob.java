package uk.gov.hmcts.juror.job.execution.jobs.dashboard.ams;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.juror.job.execution.client.contracts.SchedulerServiceClient;
import uk.gov.hmcts.juror.job.execution.jobs.ParallelJob;
import uk.gov.hmcts.juror.job.execution.jobs.dashboard.ams.data.DashboardData;
import uk.gov.hmcts.juror.job.execution.service.contracts.DatabaseService;
import uk.gov.hmcts.juror.job.execution.service.contracts.SmtpService;
import uk.gov.hmcts.juror.job.execution.util.FileUtils;

import java.time.Clock;
import java.util.List;

@Component
@Getter
@Slf4j
public class AmsDashboardGenerateJob extends ParallelJob {

    private final SchedulerServiceClient schedulerServiceClient;
    private final DatabaseService databaseService;
    private final AmsDashboardConfig config;
    private final Clock clock;
    private final SmtpService smtpService;

    @Autowired
    public AmsDashboardGenerateJob(SchedulerServiceClient schedulerServiceClient,
                                   DatabaseService databaseService,
                                   SmtpService smtpService,
                                   AmsDashboardConfig config, Clock clock) {
        super();
        this.schedulerServiceClient = schedulerServiceClient;
        this.smtpService = smtpService;
        this.databaseService = databaseService;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public List<ResultSupplier> getResultSuppliers() {
        DashboardData dashboardData =
            new DashboardData(schedulerServiceClient, databaseService, smtpService, config, clock);
        return List.of(
            new ResultSupplier(
                true,
                List.of(
                    metaData -> dashboardData.getBureauLettersAutomaticallyGenerated().populate(),
                    metaData -> dashboardData.getBureauLettersToBePrinted().populate(),
                    metaData -> dashboardData.getPncCheck().populate(),
                    metaData -> dashboardData.getExpenses().populate(),
                    metaData -> dashboardData.getCertificates().populate(),
                    metaData -> dashboardData.getAutoSys().populate(),
                    metaData -> dashboardData.getErrorsOvernight().populate(),
                    metaData -> dashboardData.getHouseKeeping().populate()
                )
            ),
            new ResultSupplier(false,
                List.of(
                    metaData -> generateDashboardFile(dashboardData)
                )
            )
        );
    }

    Result generateDashboardFile(DashboardData dashboardData) {
        try {
            String dashboardCsv = dashboardData.toCsv(clock);
            FileUtils.writeToFile(config.getDashboardCsvLocation(), dashboardCsv);
            return Result.passed();
        } catch (Exception e) {
            log.error("Failed to create dashboard csv file", e);
            return Result.failed("Failed to create dashboard csv", e);
        }
    }
}