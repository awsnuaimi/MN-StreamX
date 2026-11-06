package mnstudio.apps.mnstreamx.data.subscription

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.testing.FakeSubscriptions
import org.junit.Assert.*
import org.junit.Test

class SubscriptionRulesTest {
    @Test fun theAccountLimitIsEnforcedWithoutChangingExistingAccounts() {
        var catalog = Subscriptions(listOf(Subscription(Subscriptions.DEFAULT_ID, "Original", FakeSubscriptions.account("A"))), Subscriptions.DEFAULT_ID)
        repeat(Subscriptions.LIMIT - 1) { catalog = SubscriptionChanges.save(catalog, null, "Account $it", FakeSubscriptions.account("B$it")) }
        val original = catalog
        val error = runCatching { SubscriptionChanges.save(catalog, null, "Overflow", FakeSubscriptions.account("overflow")) }.exceptionOrNull()
        assertEquals(SubscriptionProblem.LIMIT, (error as SubscriptionException).problem)
        assertEquals(original, catalog)
    }
    @Test fun subscriptionAndSettingsDescriptionsDoNotExposeCredentials() {
        val account = AppSettings(source = PlaylistSource.XTREAM, xtreamServer = "https://synthetic.invalid",
            xtreamUsername = "synthetic-user", xtreamPassword = "synthetic-secret")
        val description = Subscription(Subscriptions.DEFAULT_ID, "Name", account).toString() + account.toString()
        assertFalse(description.contains(account.xtreamServer)); assertFalse(description.contains(account.xtreamUsername))
        assertFalse(description.contains(account.xtreamPassword))
    }
}