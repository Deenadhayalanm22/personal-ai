package com.apps.deen_sa.integration;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.core.state.StateChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
public abstract class AbstractIntegrationTestProperties {

	@Autowired
	private Flyway flyway;

	@Autowired
	protected StateChangeRepository stateChangeRepository;

	@Autowired
	private ConversationContext conversationContext;

	@BeforeEach
	protected void resetDatabaseAndConversation() {
		flyway.clean();
		flyway.migrate();
		conversationContext.reset();
	}

}
