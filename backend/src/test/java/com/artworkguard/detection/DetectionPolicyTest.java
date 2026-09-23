package com.artworkguard.detection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DetectionPolicyTest {
 final DetectionPolicy policy=new DetectionPolicy(.75,.85,.92);
 @Test void boundariesAreInclusive(){
  assertEquals(DetectionPolicy.Severity.MEDIUM,policy.severity(.75));
  assertEquals(DetectionPolicy.Severity.MEDIUM,policy.severity(Math.nextDown(.85)));
  assertEquals(DetectionPolicy.Severity.HIGH,policy.severity(.85));
  assertEquals(DetectionPolicy.Severity.HIGH,policy.severity(Math.nextDown(.92)));
  assertEquals(DetectionPolicy.Severity.CRITICAL,policy.severity(.92));
  assertEquals(DetectionPolicy.Severity.CRITICAL,policy.severity(1));
 }
 @Test void rejectsNonMatchesAndInvalidNumbers(){
  for(double score:new double[]{.749,Double.NaN,Double.POSITIVE_INFINITY,1.01,-1})
   assertThrows(IllegalArgumentException.class,()->policy.severity(score));
 }
 @Test void rejectsInvalidThresholdConfiguration(){
  assertThrows(IllegalArgumentException.class,()->new DetectionPolicy(.9,.8,.92));
  assertThrows(IllegalArgumentException.class,()->new DetectionPolicy(.75,.75,.92));
  assertThrows(IllegalArgumentException.class,()->new DetectionPolicy(.75,.85,1.1));
  assertThrows(IllegalArgumentException.class,()->new DetectionPolicy(Double.NaN,.85,.92));
 }
}
