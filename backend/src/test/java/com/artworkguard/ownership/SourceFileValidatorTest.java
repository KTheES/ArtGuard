package com.artworkguard.ownership;
import com.artworkguard.artwork.service.ArtworkException;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class SourceFileValidatorTest {
 final SourceFileValidator validator=new SourceFileValidator();
 static byte[] psd(){var b=ByteBuffer.allocate(41);b.putInt(0x38425053).putShort((short)1).put(new byte[6]).putShort((short)1).putInt(1).putInt(1).putShort((short)8).putShort((short)1).putInt(0).putInt(0).putInt(0).putShort((short)0).put((byte)0);return b.array();}
 static byte[] zip(String name,int size)throws IOException{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry(name));byte[] block=new byte[8192];for(int i=0;i<size;i+=block.length)zip.write(block,0,Math.min(block.length,size-i));zip.closeEntry();}return bytes.toByteArray();}
 @Test void psdHeaderAndSectionsAccepted(){assertEquals("PSD_HEADER_AND_SECTION_BOUNDS_ONLY",validator.validate(SourceFileValidator.Format.PSD,psd()));}
 @Test void psbTruncationAndOverflowRejected(){byte[] version=psd();version[5]=2;byte[] overflow=psd();ByteBuffer.wrap(overflow).putInt(26,-1);for(byte[] bad:new byte[][]{new byte[0],new byte[40],version,overflow})assertThrows(ArtworkException.class,()->validator.validate(SourceFileValidator.Format.PSD,bad));}
 @Test void archiveIsNotClaimedToBeAuthenticProcreate()throws Exception{assertEquals("ZIP_CONTAINER_ONLY_NOT_PROCREATE_AUTHENTICATED",validator.validate(SourceFileValidator.Format.PROCREATE,zip("data",20)));}
 @Test void archiveTraversalAndTruncationRejected()throws Exception{byte[] good=zip("data",20);for(byte[] bad:new byte[][]{zip("../escape",20),zip("C:/escape",20),java.util.Arrays.copyOf(good,good.length-15)})assertThrows(ArtworkException.class,()->validator.validate(SourceFileValidator.Format.PROCREATE,bad));}
 @Test void expansionLimitRejectsCompressedBomb()throws Exception{assertThrows(ArtworkException.class,()->validator.validate(SourceFileValidator.Format.PROCREATE,zip("data",65*1024*1024)));}
 @Test void uploadSizeLimitEnforced(){assertThrows(ArtworkException.class,()->validator.validate(SourceFileValidator.Format.PSD,new byte[SourceFileValidator.MAX_BYTES+1]));}
}
