package com.artworkguard.detection.queue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
@Configuration @EnableKafka @EnableScheduling
@ConditionalOnProperty(name="artworkguard.detection.enabled",havingValue="true")
public class DetectionKafkaConfig {
 @Bean NewTopic detectionTopic(){return TopicBuilder.name("detection.requested").partitions(1).replicas(1).build();}
 @Bean NewTopic detectionDlt(){return TopicBuilder.name("detection.requested.dlt").partitions(1).replicas(1).build();}
 @Bean ConcurrentKafkaListenerContainerFactory<String,String> detectionKafkaListenerContainerFactory(
  KafkaProperties properties,KafkaTemplate<String,String> kafka,DetectionJobService jobs,ObjectMapper mapper) {
  var config=properties.buildConsumerProperties(null);
  config.put("enable.auto.commit",false);config.put("auto.offset.reset","earliest");
  config.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
  config.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
  config.put("max.poll.records",1);config.put("max.poll.interval.ms",600000);
  var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();
  factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
  factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
  var dlt=new DeadLetterPublishingRecoverer(kafka,(record,error)->new TopicPartition("detection.requested.dlt",0));
  dlt.setFailIfSendResultIsError(true);
  var handler=new DefaultErrorHandler((record,error)->{
   dlt.accept(record,error);
   DetectionEvent event;
   try{event=DetectionEvent.parse(mapper,(String)record.value());}catch(IllegalArgumentException invalid){return;}
   jobs.fail(event.payload().jobId(),event.payload().generation(),"DETECTION_PROCESSING_FAILED");
  },new FixedBackOff(2000,2));
  handler.addNotRetryableExceptions(IllegalArgumentException.class);
  factory.setCommonErrorHandler(handler);
  return factory;
 }
}
