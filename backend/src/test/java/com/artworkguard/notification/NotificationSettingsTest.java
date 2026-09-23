package com.artworkguard.notification;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationSettingsTest {
 @Test void disabledAllowsMissingSender(){assertFalse(new NotificationSettings(false,"","http://127.0.0.1:3000").enabled());}
 @Test void enabledRequiresSender(){assertThrows(IllegalArgumentException.class,()->new NotificationSettings(true,"","https://app.example"));}
 @Test void rejectsUnsafeAppUrls(){for(String url:new String[]{"http://app.example","javascript:alert(1)","https://user@app.example","https://app.example/#fragment"})assertThrows(IllegalArgumentException.class,()->new NotificationSettings(false,"",url));}
 @Test void acceptsHttpsConfiguration(){var settings=new NotificationSettings(true,"alerts@example.com","https://app.example");assertEquals("alerts@example.com",settings.from());assertFalse(settings.toString().contains("alerts@example.com"));}
}
