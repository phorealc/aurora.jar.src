/*    */ package dev.quelchi.aurora.loader;
/*    */ 
/*    */ import java.io.InputStream;
/*    */ import java.nio.file.Files;
/*    */ import java.nio.file.Path;
/*    */ import java.security.KeyFactory;
/*    */ import java.security.PublicKey;
/*    */ import java.security.Signature;
/*    */ import java.security.spec.X509EncodedKeySpec;
/*    */ import java.util.Base64;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ final class PayloadSignature
/*    */ {
/*    */   private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEA0wG6QUmy3+uBU3PdOo0woCtJSw9K4/KjJZPXABm3rrY=";
/*    */   
/*    */   static Path sigFor(Path jar) {
/* 33 */     return jar.resolveSibling(String.valueOf(jar.getFileName()) + ".sig");
/*    */   }
/*    */   
/*    */   static boolean verify(Path jar) {
/* 37 */     Path sig = sigFor(jar);
/*    */     try {
/* 39 */       return (Files.isRegularFile(sig, new java.nio.file.LinkOption[0]) && verify(jar, Files.readString(sig)));
/* 40 */     } catch (Exception e) {
/* 41 */       Log.error("Could not read the signature beside " + String.valueOf(jar.getFileName()), e);
/* 42 */       return false;
/*    */     } 
/*    */   }
/*    */   
/*    */   static boolean verify(Path jar, String sig) {
/* 47 */     if (sig == null || sig.isBlank()) return false;
/*    */     
/*    */     try {
/* 50 */       PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode("MCowBQYDK2VwAyEA0wG6QUmy3+uBU3PdOo0woCtJSw9K4/KjJZPXABm3rrY=")));
/* 51 */       Signature s = Signature.getInstance("Ed25519");
/* 52 */       s.initVerify(key);
/* 53 */       InputStream in = Files.newInputStream(jar, new java.nio.file.OpenOption[0]); 
/* 54 */       try { byte[] buf = new byte[65536];
/*    */         int n;
/* 56 */         for (; (n = in.read(buf)) > 0; s.update(buf, 0, n));
/* 57 */         if (in != null) in.close();  } catch (Throwable throwable) { if (in != null)
/* 58 */           try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  return s.verify(Base64.getDecoder().decode(sig.trim()));
/* 59 */     } catch (Exception e) {
/* 60 */       Log.error("Could not check the signature on " + String.valueOf(jar.getFileName()), e);
/* 61 */       return false;
/*    */     } 
/*    */   }
/*    */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\PayloadSignature.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */