package com.artworkguard.detection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class DetectionPolicy {
 public enum Severity { MEDIUM,HIGH,CRITICAL }
 private final double medium,high,critical;
 public DetectionPolicy(@Value("${artworkguard.detection.medium:0.75}") double medium,
  @Value("${artworkguard.detection.high:0.85}") double high,@Value("${artworkguard.detection.critical:0.92}") double critical){
  if(!Double.isFinite(medium)||!Double.isFinite(high)||!Double.isFinite(critical)||medium<0||medium>=high||high>=critical||critical>1)
   throw new IllegalArgumentException("Detection thresholds must satisfy 0 <= medium < high < critical <= 1");
  this.medium=medium;this.high=high;this.critical=critical;
 }
 public double medium(){return medium;}public double high(){return high;}public double critical(){return critical;}
 public Severity severity(double score){
  if(!Double.isFinite(score)||score<medium||score>1)throw new IllegalArgumentException("Similarity is outside detection range");
  return score>=critical?Severity.CRITICAL:score>=high?Severity.HIGH:Severity.MEDIUM;
 }
}
