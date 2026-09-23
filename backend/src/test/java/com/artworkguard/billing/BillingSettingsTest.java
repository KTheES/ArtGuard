package com.artworkguard.billing;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class BillingSettingsTest {
 @Test void disabledBillingNeverExposesSecrets(){var s=new BillingSettings(false,"sk_test_secret","whsec_secret","pc","pp","pb",URI.create("http://localhost/s"),URI.create("http://localhost/c"));assertThrows(BillingException.class,s::secretKey);assertThrows(BillingException.class,s::webhookSecret);}
 @Test void mapsOnlyPaidPlans(){var s=new BillingSettings(true,"sk_test_secret","whsec_secret","pc","pp","pb",URI.create("http://localhost/s"),URI.create("http://localhost/c"));assertEquals("pc",s.price("CREATOR"));assertEquals("pp",s.price("PRO"));assertEquals("pb",s.price("BUSINESS"));assertThrows(BillingException.class,()->s.price("FREE"));}
}
