package com.artworkguard.detection;
public class DetectionException extends RuntimeException {
 public DetectionException(){super("작품 임베딩이 준비되지 않았습니다. 임베딩 상태를 확인해 주세요.");}
}
