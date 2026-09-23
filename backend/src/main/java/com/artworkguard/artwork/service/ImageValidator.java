package com.artworkguard.artwork.service;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
@Component
public class ImageValidator {
 public record ValidatedImage(byte[] original,byte[] thumbnail,int width,int height) {}
 public ValidatedImage validate(byte[] bytes,String contentType) {
  if(bytes.length==0 || bytes.length>20971520)throw ArtworkException.invalidImage();
  boolean png=bytes.length>=8 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71 && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10;
  boolean jpeg=bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
  if(!(png && "image/png".equals(contentType)) && !(jpeg && "image/jpeg".equals(contentType)))throw ArtworkException.invalidImage();
  try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw ArtworkException.invalidImage();
   var reader=readers.next();
   try {
    reader.setInput(input,true,true);
    int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>16000000)throw ArtworkException.invalidImage();
    var decoded=reader.read(0);
    var original=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
    var graphics=original.createGraphics();graphics.drawImage(decoded,0,0,null);graphics.dispose();
    double scale=Math.min(1.0,512.0/Math.max(width,height));
    var thumb=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_ARGB);
    var g=thumb.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(original,0,0,thumb.getWidth(),thumb.getHeight(),null);
    g.setColor(new Color(0,0,0,150));g.fillRect(0,Math.max(0,thumb.getHeight()-28),thumb.getWidth(),28);
    g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(8,Math.min(16,thumb.getWidth()/9))));
    g.drawString("ArtworkGuard",4,Math.max(10,thumb.getHeight()-8));g.dispose();
    return new ValidatedImage(encode(original),encode(thumb),width,height);
   } finally {reader.dispose();}
  } catch(IOException|IllegalArgumentException e){throw ArtworkException.invalidImage();}
 }
 private byte[] encode(BufferedImage image)throws IOException {
  var out=new ByteArrayOutputStream();
  if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");
  return out.toByteArray();
 }
}
