package com.artworkguard.common.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/** Instance-local circuit; one probe is admitted after the open interval. */
public final class CallCircuit {
 private final int threshold;
 private final Duration cooldown;
 private final Clock clock;
 private int failures;
 private long generation;
 private Instant reopenAt;
 private boolean probing;

 public CallCircuit(int threshold,Duration cooldown,Clock clock){
  if(threshold<1||cooldown.isNegative()||cooldown.isZero())throw new IllegalArgumentException("Invalid circuit policy");
  this.threshold=threshold;this.cooldown=cooldown;this.clock=clock;
 }
 public <T> T call(Supplier<T> operation){
  long ticket;
  synchronized(this){
   if(reopenAt!=null){
    if(clock.instant().isBefore(reopenAt)||probing)throw new OpenCircuitException();
    probing=true;
   }
   ticket=generation;
  }
  try{
   T result=operation.get();
   synchronized(this){if(ticket==generation){failures=0;if(probing){reopenAt=null;probing=false;generation++;}}}
   return result;
  }catch(RuntimeException e){
   synchronized(this){
    if(ticket==generation&&(probing||++failures>=threshold)){
     reopenAt=clock.instant().plus(cooldown);probing=false;generation++;
    }
   }
   throw e;
  }
 }
 public static final class OpenCircuitException extends IllegalStateException {
  public OpenCircuitException(){super("External service circuit is open");}
 }
}
