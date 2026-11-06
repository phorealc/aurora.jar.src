/*     */ package dev.quelchi.aurora.loader;
/*     */ 
/*     */ import java.io.IOException;
/*     */ import java.io.InputStream;
/*     */ import java.net.URI;
/*     */ import java.net.http.HttpRequest;
/*     */ import java.net.http.HttpResponse;
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.channels.FileChannel;
/*     */ import java.nio.charset.StandardCharsets;
/*     */ import java.nio.file.CopyOption;
/*     */ import java.nio.file.Files;
/*     */ import java.nio.file.OpenOption;
/*     */ import java.nio.file.Path;
/*     */ import java.nio.file.StandardCopyOption;
/*     */ import java.nio.file.StandardOpenOption;
/*     */ import java.nio.file.attribute.FileAttribute;
/*     */ import java.time.Duration;
/*     */ import java.util.zip.ZipEntry;
/*     */ import java.util.zip.ZipFile;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ final class ContainerUpdater
/*     */ {
/*     */   private static final String VERSION_ENDPOINT = "/cosmetics/version";
/*     */   private static final String JAR_ENDPOINT = "/cosmetics/jar";
/*     */   private static final String CONTAINER_ID = "auroraloader";
/*  67 */   private static final Duration BUDGET = Duration.ofSeconds(60L);
/*     */ 
/*     */ 
/*     */   
/*     */   private static final int SWAP_TRIES = 60;
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   static void update(Path auroraDir, Path self) {
/*     */     try {
/*  78 */       if (self == null || !Files.isRegularFile(self, new java.nio.file.LinkOption[0]))
/*  79 */         return;  Path staged = auroraDir.resolve("container.staged");
/*  80 */       Path refusedMemo = auroraDir.resolve("container.refused");
/*     */ 
/*     */ 
/*     */       
/*  84 */       if (Files.isRegularFile(staged, new java.nio.file.LinkOption[0])) {
/*  85 */         applyStaged(staged, self);
/*     */         
/*     */         return;
/*     */       } 
/*  89 */       String local = PayloadUpdater.sha256(self);
/*  90 */       HttpResponse<String> res = PayloadUpdater.HTTP.send(
/*  91 */           HttpRequest.newBuilder(URI.create("https://auroramod.xyz/cosmetics/version"))
/*  92 */           .timeout(Duration.ofSeconds(10L)).GET().build(), 
/*  93 */           HttpResponse.BodyHandlers.ofString());
/*  94 */       if (res.statusCode() / 100 != 2)
/*     */         return; 
/*  96 */       String remote = PayloadUpdater.jsonString(res.body(), "sha256");
/*  97 */       if (remote == null || remote.isEmpty() || remote.equals(local)) {
/*     */         return;
/*     */       }
/* 100 */       if (remote.equals(readMemo(refusedMemo)))
/*     */         return; 
/* 102 */       Log.info("Container update: " + local.substring(0, 8) + " -> " + remote.substring(0, 8));
/*     */       
/* 104 */       Files.createDirectories(auroraDir, (FileAttribute<?>[])new FileAttribute[0]);
/* 105 */       Path tmp = auroraDir.resolve("container." + ProcessHandle.current().pid() + ".part");
/* 106 */       long size = PayloadUpdater.jsonLong(res.body(), "size");
/* 107 */       if (!PayloadUpdater.download("https://auroramod.xyz/cosmetics/jar", tmp, size, null, BUDGET)) {
/* 108 */         Files.deleteIfExists(tmp);
/* 109 */         Log.warn("Container download failed, keeping the one on disk");
/*     */         
/*     */         return;
/*     */       } 
/* 113 */       if (!remote.equals(PayloadUpdater.sha256(tmp))) {
/* 114 */         Files.deleteIfExists(tmp);
/* 115 */         Log.warn("Container failed checksum verification, discarded");
/*     */         
/*     */         return;
/*     */       } 
/*     */       
/* 120 */       if (!PayloadSignature.verify(tmp, PayloadUpdater.jsonString(res.body(), "sig"))) {
/* 121 */         Files.deleteIfExists(tmp);
/* 122 */         Log.warn("Container update is not signed by Aurora, discarded");
/*     */         
/*     */         return;
/*     */       } 
/*     */       
/* 127 */       String id = modId(tmp);
/* 128 */       if (!"auroraloader".equals(id)) {
/* 129 */         Files.deleteIfExists(tmp);
/* 130 */         writeMemo(refusedMemo, remote);
/* 131 */         Log.warn("The mods jar endpoint served id " + id + ", not auroraloader. Refusing to install it over the loader. Remembered, so it will not be downloaded again.");
/*     */ 
/*     */         
/*     */         return;
/*     */       } 
/*     */       
/* 137 */       Files.move(tmp, staged, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
/* 138 */       Files.deleteIfExists(refusedMemo);
/* 139 */       applyStaged(staged, self);
/* 140 */     } catch (Throwable t) {
/* 141 */       Log.error("Container update failed, keeping the one on disk", t);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static String modId(Path jar) {
/*     */     
/* 153 */     try { ZipFile zip = new ZipFile(jar.toFile()); 
/* 154 */       try { ZipEntry entry = zip.getEntry("fabric.mod.json");
/* 155 */         if (entry == null) { String str = null;
/*     */ 
/*     */ 
/*     */ 
/*     */           
/* 160 */           zip.close(); return str; }  InputStream in = zip.getInputStream(entry); try { String body = new String(in.readAllBytes(), StandardCharsets.UTF_8); String str1 = PayloadUpdater.jsonString(body, "id"); if (in != null) in.close();  zip.close(); return str1; } catch (Throwable throwable) { if (in != null) try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  } catch (Throwable throwable) { try { zip.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }  throw throwable; }  } catch (Exception e)
/* 161 */     { return null; }
/*     */   
/*     */   }
/*     */   
/*     */   private static String readMemo(Path memo) {
/*     */     try {
/* 167 */       return Files.isRegularFile(memo, new java.nio.file.LinkOption[0]) ? Files.readString(memo).trim() : "";
/* 168 */     } catch (Exception e) {
/* 169 */       return "";
/*     */     } 
/*     */   }
/*     */   
/*     */   private static void writeMemo(Path memo, String sha) {
/*     */     try {
/* 175 */       Files.writeString(memo, sha, new OpenOption[0]);
/* 176 */     } catch (Exception exception) {}
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static void applyStaged(Path staged, Path self) {
/* 205 */     Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { byte[] fresh = Files.readAllBytes(staged); FileChannel ch = FileChannel.open(self, new OpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING }); try { ByteBuffer buf = ByteBuffer.wrap(fresh); while (buf.hasRemaining())
/*     */                   ch.write(buf);  if (ch != null)
/*     */                   ch.close();  }
/* 208 */               catch (Throwable throwable) { if (ch != null) try { ch.close(); } catch (Throwable throwable1)
/*     */                   { throwable.addSuppressed(throwable1); }
/*     */                     throw throwable; }
/*     */               
/*     */               Files.delete(staged); }
/* 213 */             catch (IOException iOException) {}
/*     */           }"aurora-container-update"));
/*     */ 
/*     */     
/* 217 */     Log.info("Container update staged, installing it when the game closes");
/*     */   }
/*     */ 
/*     */   
/*     */   static void sweep(Path self) {
/* 222 */     if (self == null)
/*     */       return;  try {
/* 224 */       Files.deleteIfExists(self.resolveSibling(String.valueOf(self.getFileName()) + ".old"));
/* 225 */     } catch (IOException iOException) {}
/*     */   }
/*     */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\ContainerUpdater.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */