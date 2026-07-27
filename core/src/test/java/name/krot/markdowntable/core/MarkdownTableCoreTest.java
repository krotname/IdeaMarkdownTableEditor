// SPDX-License-Identifier: MIT
// Copyright (c) 2026 krotname

package name.krot.markdowntable.core;

import org.junit.jupiter.api.Test;

final class MarkdownTableCoreTest {
	@Test
	void behavioralScenarios() {
		MarkdownTableCoreScenarios.run();
	}

	@Test
	void goldenFixtures() throws Exception {
		MarkdownTableGoldenFixtures.run();
	}
}
