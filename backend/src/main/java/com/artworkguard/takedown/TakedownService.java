package com.artworkguard.takedown;
import com.artworkguard.detection.*;
import com.artworkguard.evidence.EvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.artworkguard.takedown.TakedownDtos.*;

@Service
public class TakedownService {
 private final DetectionReviewService detections; private final EvidenceService evidence;
 private final TakedownRepository repository; private final ObjectMapper mapper;
 private final com.artworkguard.auth.verification.EmailVerificationService verification;
 public TakedownService(DetectionReviewService detections,EvidenceService evidence,TakedownRepository repository,ObjectMapper mapper,com.artworkguard.auth.verification.EmailVerificationService verification){
  this.detections=detections;this.evidence=evidence;this.repository=repository;this.mapper=mapper;
  this.verification=verification;
 }
 @Transactional
 public Report create(UUID owner,UUID detection,Create request){
  var detail=detections.detail(owner,detection);
  verification.requireVerified(owner);
  if(!Boolean.TRUE.equals(request.rightsConfirmed()) || detail.detection().synthetic())
   throw new TakedownException("실제 탐지 결과와 신고 권한 확인이 필요합니다.");
  if(!repository.lockConfirmed(owner,detection,request.detectionVersion()))
   throw new TakedownException("탐지를 CONFIRMED로 검토한 뒤 최신 버전으로 요청해 주세요.");
  var snapshot=evidence.list(owner,detection).stream().filter(e->e.id().equals(request.evidenceId())).findFirst()
   .orElseThrow(()->new TakedownException("해당 탐지의 보존 증거가 필요합니다."));
  var existing=repository.find(owner,detection);
  if(existing.isPresent()){
   if(!existing.get().evidenceId().equals(request.evidenceId()))throw new TakedownException("이미 다른 증거로 초안이 생성되었습니다.");
   return existing.get();
  }
  var draft=mapper.createObjectNode();
  draft.put("schema","artworkguard-report-draft-v1");
  draft.put("artworkTitle",detail.detection().artworkTitle());
  draft.put("artworkId",detail.detection().artworkId().toString());
  draft.put("confirmedDetectionVersion",request.detectionVersion());
  draft.put("rightsConfirmedBy",owner.toString());
  draft.set("evidence",mapper.valueToTree(snapshot));
  if("ALIEXPRESS".equals(snapshot.marketplace()))draft.put("marketplaceReportUrl","https://ipp.alibabagroup.com/");
  else draft.putNull("marketplaceReportUrl");
  draft.put("text","신고 준비 초안입니다. 원본 작품과 상품 증거를 비교하고 권리 보유 또는 대리 권한, 허락 여부 및 마켓 요구사항을 직접 확인해 주세요.");
  draft.putArray("requiredUserInput").add("신고자 정보").add("권리 보유 또는 대리 권한 자료").add("원본 작품 자료 및 게시 정보").add("신고 사유와 마켓 요구 진술");
  draft.put("automaticallySubmitted",false);
  repository.create(owner,detection,snapshot.id(),draft.toString());
  return repository.find(owner,detection).orElseThrow();
 }
 @Transactional(readOnly=true)
 public Optional<Report> get(UUID owner,UUID detection){
  detections.detail(owner,detection);evidence.list(owner,detection);return repository.find(owner,detection);
 }
 @Transactional
 public Report update(UUID owner,UUID detection,Update request){
  var current=get(owner,detection).orElseThrow(()->new TakedownException("신고 초안이 없습니다."));
  if(!allowed(current.status(),request.status()))throw new TakedownException("허용되지 않는 신고 상태 변경입니다.");
  if(request.status()==Status.SUBMITTED){
   verification.requireVerified(owner);
   var detail=detections.detail(owner,detection);
   if(!repository.lockConfirmed(owner,detection,detail.detection().version()))throw new TakedownException("제출 기록 전 탐지 확인이 필요합니다.");
   if(request.externalReference()==null || request.externalReference().isBlank())throw new TakedownException("직접 제출한 마켓 접수번호를 입력해 주세요.");
  }else if(request.externalReference()!=null){throw new TakedownException("접수번호는 제출 기록 시에만 입력할 수 있습니다.");}
  if(!repository.update(owner,detection,request))throw new TakedownException("신고 상태가 변경되었습니다. 다시 조회해 주세요.");
  return repository.find(owner,detection).orElseThrow();
 }
 static boolean allowed(Status from,Status to){
  return from==Status.DRAFT && (to==Status.SUBMITTED || to==Status.WITHDRAWN)
   || from==Status.SUBMITTED && Set.of(Status.RESOLVED,Status.REJECTED,Status.WITHDRAWN).contains(to);
 }
}
