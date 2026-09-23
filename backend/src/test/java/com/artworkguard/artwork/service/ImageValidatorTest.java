package com.artworkguard.artwork.service;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
class ImageValidatorTest {
 private final ImageValidator validator=new ImageValidator();
 public static byte[] png(int width,int height)throws IOException {
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
 }
 @Test void decodesAndCreatesSmallerWatermarkedPreview()throws Exception {
  var result=validator.validate(png(1024,768),"image/png");
  assertThat(result.width()).isEqualTo(1024);
  var thumb=ImageIO.read(new ByteArrayInputStream(result.thumbnail()));
  assertThat(thumb.getWidth()).isEqualTo(512);assertThat(thumb.getHeight()).isEqualTo(384);
  assertThat(result.original()).isNotEmpty();assertThat(result.thumbnail()).isNotEqualTo(result.original());
 }
 @Test void rejectsMimeSpoofing()throws Exception {
  assertThatThrownBy(()->validator.validate(png(10,10),"image/jpeg")).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->validator.validate("<svg>not an image</svg>".getBytes(),"image/png")).isInstanceOf(ArtworkException.class);
 }
 @Test void rejectsTruncatedFile()throws Exception {
  byte[] data=java.util.Arrays.copyOf(png(10,10),20);
  assertThatThrownBy(()->validator.validate(data,"image/png")).isInstanceOf(ArtworkException.class);
 }
 @Test void rejectsDimensionsBeforeDecodingPixelData()throws Exception {
  assertThatThrownBy(()->validator.validate(png(8193,1),"image/png")).isInstanceOf(ArtworkException.class);
 }
 @Test void rejectsOversizedAndEmptyUploads() {
  assertThatThrownBy(()->validator.validate(new byte[20971521],"image/png")).isInstanceOf(ArtworkException.class);
  assertThatThrownBy(()->validator.validate(new byte[0],"image/png")).isInstanceOf(ArtworkException.class);
 }
}
