package com.artworkguard.common.resilience;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallCircuitTest {
 final Clock clock=mock(Clock.class);
 final Instant now=Instant.parse("2026-09-10T00:00:00Z");
 final CallCircuit circuit=new CallCircuit(2,Duration.ofSeconds(30),clock);
 void open(){
  when(clock.instant()).thenReturn(now);
  for(int i=0;i<2;i++)assertThrows(IllegalStateException.class,()->circuit.call(()->{throw new IllegalStateException("down");}));
 }
 @Test void opensAfterThresholdAndSkipsRemoteCall(){
  open();var calls=new AtomicInteger();
  assertThrows(CallCircuit.OpenCircuitException.class,()->circuit.call(calls::incrementAndGet));
  assertEquals(0,calls.get());
 }
 @Test void successfulProbeClosesCircuit(){
  open();when(clock.instant()).thenReturn(now.plusSeconds(30));
  assertEquals("recovered",circuit.call(()->"recovered"));
  assertEquals("normal",circuit.call(()->"normal"));
 }
 @Test void failedProbeStartsNewCooldown(){
  open();when(clock.instant()).thenReturn(now.plusSeconds(30));
  assertThrows(IllegalArgumentException.class,()->circuit.call(()->{throw new IllegalArgumentException();}));
  when(clock.instant()).thenReturn(now.plusSeconds(59));
  assertThrows(CallCircuit.OpenCircuitException.class,()->circuit.call(()->true));
 }
 @Test void onlyOneConcurrentProbeIsAllowed()throws Exception{
  open();when(clock.instant()).thenReturn(now.plusSeconds(30));
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()){
   var probe=executor.submit(()->circuit.call(()->{
    entered.countDown();
    try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    return true;
   }));
   try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertThrows(CallCircuit.OpenCircuitException.class,()->circuit.call(()->true));}
   finally{release.countDown();}
   assertTrue(probe.get(5,TimeUnit.SECONDS));
  }
 }
 @Test void successfulClosedCallResetsFailureCount(){
  when(clock.instant()).thenReturn(now);
  assertThrows(IllegalStateException.class,()->circuit.call(()->{throw new IllegalStateException();}));
  circuit.call(()->true);
  assertThrows(IllegalStateException.class,()->circuit.call(()->{throw new IllegalStateException();}));
  assertTrue(circuit.call(()->true));
 }
}
