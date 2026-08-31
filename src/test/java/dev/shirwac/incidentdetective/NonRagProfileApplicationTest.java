package dev.shirwac.incidentdetective;

import dev.shirwac.incidentdetective.investigation.tools.FixtureRunbookRetrievalStrategy;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalStrategy;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.InMemoryGlobalDailyLiveQuota;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class NonRagProfileApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RunbookRetrievalStrategy retrieval;

    @Autowired
    private GlobalDailyLiveQuota dailyLiveQuota;

    @Test
    void anyNonRagProfileRemainsDatabaseFree() {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertInstanceOf(FixtureRunbookRetrievalStrategy.class, retrieval);
        assertInstanceOf(InMemoryGlobalDailyLiveQuota.class, dailyLiveQuota);
    }
}
