/*     */ package dev.quelchi.aurora.loader;
/*     */ 
/*     */ import java.io.IOException;
/*     */ import java.io.InputStream;
/*     */ import java.io.OutputStream;
/*     */ import java.net.URI;
/*     */ import java.net.http.HttpClient;
/*     */ import java.net.http.HttpRequest;
/*     */ import java.net.http.HttpResponse;
/*     */ import java.nio.file.CopyOption;
/*     */ import java.nio.file.Files;
/*     */ import java.nio.file.Path;
/*     */ import java.nio.file.StandardCopyOption;
/*     */ import java.nio.file.attribute.FileAttribute;
/*     */ import java.security.MessageDigest;
/*     */ import java.time.Duration;
/*     */ import java.util.Properties;
/*     */ import java.util.zip.ZipEntry;
/*     */ import java.util.zip.ZipException;
/*     */ import java.util.zip.ZipFile;
/*     */ import net.fabricmc.loader.api.FabricLoader;
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
/*     */ final class PayloadUpdater
/*     */ {
/*     */   static final String BASE = "https://auroramod.xyz";
/*     */   private static final String VERSION_ENDPOINT = "/cosmetics/payload/version";
/*     */   private static final String PAYLOAD_ENDPOINT = "/cosmetics/payload";
/*  63 */   private static final Duration BUDGET = Duration.ofSeconds(45L);
/*  64 */   private static final Duration FIRST_INSTALL_BUDGET = Duration.ofMinutes(3L);
/*     */   
/*  66 */   static final HttpClient HTTP = HttpClient.newBuilder()
/*  67 */     .connectTimeout(Duration.ofSeconds(8L))
/*  68 */     .followRedirects(HttpClient.Redirect.NORMAL)
/*  69 */     .build();
/*     */ 
/*     */ 
/*     */   
/*     */   private static final int MOVE_RETRIES = 25;
/*     */ 
/*     */ 
/*     */   
/*     */   private static final long MOVE_RETRY_MS = 200L;
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static String base(Path payload) {
/*  83 */     Path override = payload.resolveSibling("payload.source");
/*     */     try {
/*  85 */       if (Files.isRegularFile(override, new java.nio.file.LinkOption[0])) {
/*  86 */         String url = Files.readString(override).trim();
/*  87 */         if (!url.isEmpty()) {
/*  88 */           Log.info("Payload source overridden: " + url);
/*  89 */           return url;
/*     */         } 
/*     */       } 
/*  92 */     } catch (Exception e) {
/*  93 */       Log.error("Could not read the payload source override", e);
/*     */     } 
/*  95 */     return "https://auroramod.xyz";
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
/*     */   static void applyStaged(Path payload) {
/* 113 */     Path staged = payload.resolveSibling(String.valueOf(payload.getFileName()) + ".staged");
/* 114 */     if (!Files.isRegularFile(staged, new java.nio.file.LinkOption[0])) {
/*     */       return;
/*     */     }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 122 */     for (int attempt = 0; attempt < 25; attempt++) {
/*     */       try {
/* 124 */         ZipFile zip = new ZipFile(staged.toFile()); 
/* 125 */         try { if (zip.getEntry("fabric.mod.json") == null)
/* 126 */           { Log.warn("Staged payload has no fabric.mod.json, discarding it");
/* 127 */             Files.deleteIfExists(staged);
/*     */ 
/*     */             
/* 130 */             zip.close(); return; }  zip.close(); } catch (Throwable throwable) { try { zip.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }  throw throwable; }
/* 131 */          Path stagedSig = PayloadSignature.sigFor(staged);
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */         
/* 137 */         long fresh = built(staged), have = Files.isRegularFile(payload, new java.nio.file.LinkOption[0]) ? built(payload) : 0L;
/* 138 */         if (fresh > 0L && have > 0L && fresh < have) {
/* 139 */           Log.info("Staged payload is older than the installed one, discarding it");
/* 140 */           Files.deleteIfExists(staged);
/* 141 */           Files.deleteIfExists(stagedSig);
/*     */           return;
/*     */         } 
/* 144 */         if (!PayloadSignature.verify(staged)) {
/* 145 */           Log.warn("Staged payload is not signed, discarding it");
/* 146 */           Files.deleteIfExists(staged);
/* 147 */           Files.deleteIfExists(stagedSig);
/*     */           return;
/*     */         } 
/* 150 */         Files.move(staged, payload, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
/* 151 */         Files.move(stagedSig, PayloadSignature.sigFor(payload), new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
/* 152 */         Log.info("Applied the payload staged by the last session");
/*     */         return;
/* 154 */       } catch (ZipException ze) {
/*     */         
/* 156 */         Log.error("Staged payload is corrupt, discarding it", ze);
/*     */         try {
/* 158 */           Files.deleteIfExists(staged);
/* 159 */         } catch (Exception exception) {}
/*     */ 
/*     */         
/*     */         return;
/* 163 */       } catch (IOException e) {
/*     */         
/* 165 */         if (!sleep(200L))
/*     */           break; 
/*     */       } 
/* 168 */     }  Log.warn("Could not apply the staged payload yet (an antivirus may be scanning it), leaving it staged for the next launch");
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   static long built(Path jar) throws IOException {
/* 178 */     ZipFile zip = new ZipFile(jar.toFile()); try {
/* 179 */       ZipEntry e = zip.getEntry("assets/aurora/aurora-build.properties");
/* 180 */       if (e == null) { long l1 = 0L;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */         
/* 187 */         zip.close(); return l1; }  Properties p = new Properties(); InputStream in = zip.getInputStream(e); try { p.load(in); if (in != null) in.close();  } catch (Throwable throwable) { if (in != null) try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  String s = p.getProperty("built", "").replaceAll("\\D", ""); long l = s.isEmpty() ? 0L : Long.parseLong(s); zip.close();
/*     */       return l;
/*     */     } catch (Throwable throwable) {
/*     */       try {
/*     */         zip.close();
/*     */       } catch (Throwable throwable1) {
/*     */         throwable.addSuppressed(throwable1);
/*     */       } 
/*     */       throw throwable;
/*     */     } 
/*     */   }
/*     */   
/*     */   private static void moveWithRetry(Path src, Path dst) throws IOException {
/* 200 */     IOException last = null;
/* 201 */     for (int attempt = 0; attempt < 25; attempt++) {
/*     */       try {
/* 203 */         Files.move(src, dst, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
/*     */         return;
/* 205 */       } catch (IOException e) {
/* 206 */         last = e;
/* 207 */         if (!sleep(200L))
/*     */           break; 
/*     */       } 
/* 210 */     }  throw (last != null) ? last : new IOException("move failed");
/*     */   }
/*     */ 
/*     */   
/*     */   private static boolean sleep(long ms) {
/*     */     try {
/* 216 */       Thread.sleep(ms);
/* 217 */       return true;
/* 218 */     } catch (InterruptedException e) {
/* 219 */       Thread.currentThread().interrupt();
/* 220 */       return false;
/*     */     } 
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
/*     */   static boolean shouldCheckAnonymously(Path payload) {
/* 242 */     if (!Files.isRegularFile(payload, new java.nio.file.LinkOption[0])) return true;
/*     */     
/*     */     try {
/* 245 */       Path file = FabricLoader.getInstance().getConfigDir().resolve("aurora-channel.txt");
/* 246 */       if (!Files.isRegularFile(file, new java.nio.file.LinkOption[0])) return true; 
/* 247 */       String channel = Files.readString(file).trim();
/* 248 */       if (channel.isEmpty() || "release".equals(channel)) return true; 
/* 249 */       Log.info("On the " + channel + " channel, leaving the payload to the in-game check");
/* 250 */       return false;
/* 251 */     } catch (Exception e) {
/* 252 */       Log.error("Could not read the remembered channel, checking anonymously", e);
/* 253 */       return true;
/*     */     } 
/*     */   }
/*     */   
/*     */   static void update(Path payload) {
/* 258 */     boolean firstInstall = !Files.isRegularFile(payload, new java.nio.file.LinkOption[0]);
/* 259 */     Progress ui = null;
/* 260 */     String base = base(payload);
/*     */     try {
/* 262 */       String local = firstInstall ? "" : sha256(payload);
/*     */       
/* 264 */       HttpResponse<String> res = HTTP.send(
/* 265 */           HttpRequest.newBuilder(URI.create(base + "/cosmetics/payload/version"))
/* 266 */           .timeout(Duration.ofSeconds(10L)).GET().build(), 
/* 267 */           HttpResponse.BodyHandlers.ofString());
/* 268 */       if (res.statusCode() / 100 != 2) {
/* 269 */         Log.info("Version check returned " + res.statusCode() + ", keeping the current payload");
/*     */         
/*     */         return;
/*     */       } 
/* 273 */       String remote = jsonString(res.body(), "sha256");
/* 274 */       if (remote == null || remote.isEmpty()) {
/* 275 */         Log.info("Version response had no sha256, keeping the current payload");
/*     */         return;
/*     */       } 
/* 278 */       String sig = jsonString(res.body(), "sig");
/* 279 */       if (remote.equals(local)) {
/* 280 */         Log.info("Payload is up to date (" + local.substring(0, 8) + ")");
/*     */ 
/*     */         
/* 283 */         if (!PayloadSignature.verify(payload) && PayloadSignature.verify(payload, sig)) {
/* 284 */           Files.writeString(PayloadSignature.sigFor(payload), sig, new java.nio.file.OpenOption[0]);
/* 285 */           Log.info("Signature fetched for the payload on disk");
/*     */         } 
/*     */         
/*     */         return;
/*     */       } 
/* 290 */       Log.info(firstInstall ? ("No payload yet, downloading " + 
/* 291 */           remote.substring(0, 8)) : ("Payload update: " + 
/* 292 */           local.substring(0, 8) + " -> " + remote.substring(0, 8)));
/*     */       
/* 294 */       ui = Progress.open();
/* 295 */       if (ui != null) {
/* 296 */         ui.status(firstInstall ? "Downloading Aurora..." : "Updating Aurora...");
/*     */       }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 306 */       Path tmp = payload.resolveSibling("aurora-payload.jar." + 
/* 307 */           ProcessHandle.current().pid() + ".part");
/* 308 */       Files.createDirectories(payload.getParent(), (FileAttribute<?>[])new FileAttribute[0]);
/* 309 */       long size = jsonLong(res.body(), "size");
/* 310 */       if (!download(base + "/cosmetics/payload", tmp, size, ui, 
/* 311 */           firstInstall ? FIRST_INSTALL_BUDGET : BUDGET)) {
/* 312 */         Files.deleteIfExists(tmp);
/* 313 */         Log.warn("Payload download failed or timed out, keeping the current payload");
/*     */ 
/*     */         
/*     */         return;
/*     */       } 
/*     */ 
/*     */       
/* 320 */       String got = sha256(tmp);
/* 321 */       if (!remote.equals(got)) {
/* 322 */         Files.deleteIfExists(tmp);
/* 323 */         Log.warn("Payload failed checksum verification, discarded");
/*     */         return;
/*     */       } 
/* 326 */       if (!PayloadSignature.verify(tmp, sig)) {
/* 327 */         Files.deleteIfExists(tmp);
/* 328 */         Log.warn("Downloaded payload is not signed by Aurora, discarded");
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */         
/*     */         return;
/*     */       } 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/*     */       try {
/* 345 */         moveWithRetry(tmp, payload);
/* 346 */       } catch (IOException e) {
/* 347 */         Files.deleteIfExists(tmp);
/* 348 */         Log.warn("Could not replace the payload after retries (another client running, or an antivirus holding it): " + String.valueOf(e) + ". Keeping the current one.");
/*     */         
/*     */         return;
/*     */       } 
/* 352 */       Files.writeString(PayloadSignature.sigFor(payload), sig, new java.nio.file.OpenOption[0]);
/* 353 */       Log.info("Payload updated to " + remote.substring(0, 8) + ", applies this launch");
/* 354 */     } catch (Throwable t) {
/* 355 */       Log.error("Payload update check failed, continuing to launch", t);
/*     */     } finally {
/* 357 */       if (ui != null) ui.close(); 
/*     */     } 
/*     */   }
/*     */   
/*     */   static boolean download(String url, Path to, long expected, Progress ui, Duration budget) {
/* 362 */     long deadline = System.currentTimeMillis() + budget.toMillis();
/*     */     try {
/* 364 */       HttpResponse<InputStream> res = HTTP.send(
/* 365 */           HttpRequest.newBuilder(URI.create(url)).timeout(budget).GET().build(), 
/* 366 */           HttpResponse.BodyHandlers.ofInputStream());
/* 367 */       if (res.statusCode() / 100 != 2) return false;
/*     */       
/* 369 */       InputStream in = res.body(); try { OutputStream out = Files.newOutputStream(to, new java.nio.file.OpenOption[0]);
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
/* 380 */         try { Thread watchdog = new Thread(() -> {
/*     */                 try {
/*     */                   long left = deadline - System.currentTimeMillis(); if (left > 0L)
/*     */                     Thread.sleep(left); 
/*     */                   in.close();
/* 385 */                 } catch (InterruptedException interruptedException) {
/*     */                 
/* 387 */                 } catch (Exception e) {
/*     */                   Log.error("Payload download watchdog failed", e);
/*     */                 } 
/*     */               }"aurora-payload-watchdog");
/* 391 */           watchdog.setDaemon(true);
/* 392 */           watchdog.start();
/*     */           
/* 394 */           try { byte[] buf = new byte[65536];
/* 395 */             long total = 0L;
/*     */             int n;
/* 397 */             while ((n = in.read(buf)) > 0)
/* 398 */             { if (System.currentTimeMillis() > deadline) { boolean bool = false;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */                 
/* 406 */                 watchdog.interrupt();
/*     */                 
/* 408 */                 if (out != null) out.close();  return bool; }  out.write(buf, 0, n); total += n; if (ui != null && expected > 0L) ui.progress((int)Math.min(100L, total * 100L / expected));  }  } finally { watchdog.interrupt(); }  if (out != null) out.close();  } catch (Throwable throwable) { if (out != null) try { out.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  if (in != null) in.close();  } catch (Throwable throwable) { if (in != null)
/* 409 */           try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  return true;
/* 410 */     } catch (Exception e) {
/* 411 */       Log.error("Payload download failed", e);
/* 412 */       return false;
/*     */     } 
/*     */   }
/*     */   
/*     */   static String sha256(Path file) throws Exception {
/* 417 */     MessageDigest digest = MessageDigest.getInstance("SHA-256");
/* 418 */     InputStream in = Files.newInputStream(file, new java.nio.file.OpenOption[0]); 
/* 419 */     try { byte[] buf = new byte[65536];
/*     */       int n;
/* 421 */       for (; (n = in.read(buf)) > 0; digest.update(buf, 0, n));
/* 422 */       if (in != null) in.close();  } catch (Throwable throwable) { if (in != null)
/* 423 */         try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  StringBuilder sb = new StringBuilder();
/* 424 */     for (byte b : digest.digest()) { sb.append(String.format("%02x", new Object[] { Byte.valueOf(b) })); }
/* 425 */      return sb.toString();
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   static String jsonString(String body, String key) {
/* 432 */     int i = body.indexOf("\"" + key + "\"");
/* 433 */     if (i < 0) return null; 
/* 434 */     int q = body.indexOf('"', body.indexOf(':', i) + 1);
/* 435 */     int end = body.indexOf('"', q + 1);
/* 436 */     return (q < 0 || end < 0) ? null : body.substring(q + 1, end);
/*     */   }
/*     */   
/*     */   static long jsonLong(String body, String key) {
/* 440 */     int i = body.indexOf("\"" + key + "\"");
/* 441 */     if (i < 0) return 0L; 
/* 442 */     int c = body.indexOf(':', i) + 1;
/* 443 */     StringBuilder sb = new StringBuilder();
/* 444 */     while (c < body.length() && (Character.isDigit(body.charAt(c)) || body.charAt(c) == ' ')) {
/* 445 */       char ch = body.charAt(c++);
/* 446 */       if (ch != ' ') sb.append(ch); 
/*     */     } 
/*     */     try {
/* 449 */       return Long.parseLong(sb.toString());
/* 450 */     } catch (Exception e) {
/* 451 */       return 0L;
/*     */     } 
/*     */   }
/*     */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\PayloadUpdater.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */