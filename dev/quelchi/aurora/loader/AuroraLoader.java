/*     */ package dev.quelchi.aurora.loader;
/*     */ 
/*     */ import java.io.IOException;
/*     */ import java.net.URL;
/*     */ import java.nio.file.CopyOption;
/*     */ import java.nio.file.Files;
/*     */ import java.nio.file.Path;
/*     */ import java.nio.file.StandardCopyOption;
/*     */ import java.nio.file.attribute.FileAttribute;
/*     */ import java.util.Comparator;
/*     */ import java.util.Objects;
/*     */ import java.util.stream.Stream;
/*     */ import net.fabricmc.loader.api.FabricLoader;
/*     */ import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
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
/*     */ public final class AuroraLoader
/*     */   implements PreLaunchEntrypoint
/*     */ {
/*     */   public void onPreLaunch() {
/*  40 */     applyPendingProfile();
/*     */     
/*  42 */     Path payload = PayloadLoader.payloadPath();
/*  43 */     boolean pinned = false;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*     */     try {
/*  53 */       Path pin = payload.resolveSibling("payload.pinned");
/*  54 */       pinned = (Boolean.getBoolean("aurora.loader.noupdate") || Files.exists(pin, new java.nio.file.LinkOption[0]));
/*  55 */       if (pinned) {
/*  56 */         Log.info("Update skipped, this install is pinned");
/*     */ 
/*     */       
/*     */       }
/*     */       else {
/*     */ 
/*     */         
/*  63 */         PayloadUpdater.applyStaged(payload);
/*     */ 
/*     */         
/*  66 */         if (PayloadUpdater.shouldCheckAnonymously(payload) || !PayloadSignature.verify(payload)) {
/*  67 */           PayloadUpdater.update(payload);
/*     */         }
/*     */       } 
/*  70 */     } catch (Throwable t) {
/*     */ 
/*     */       
/*  73 */       Log.error("Payload update failed, using the payload already on disk", t);
/*     */     } 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*  80 */     if (!pinned) {
/*     */       try {
/*  82 */         ContainerUpdater.sweep(selfJar());
/*     */         
/*  84 */         ContainerUpdater.update(payload.getParent(), selfJar());
/*  85 */       } catch (Throwable t) {
/*  86 */         Log.error("Container update check failed", t);
/*     */       } 
/*     */     }
/*     */     
/*  90 */     PayloadLoader.load(payload);
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
/*     */   private static void applyPendingProfile() {
/* 108 */     Path liveConfig = FabricLoader.getInstance().getConfigDir();
/* 109 */     Path pending = liveConfig.resolve("aurora-profiles").resolve(".pending");
/* 110 */     Path stagedConfig = pending.resolve("config");
/* 111 */     if (!Files.isDirectory(stagedConfig, new java.nio.file.LinkOption[0])) {
/*     */       return;
/*     */     }
/* 114 */     if (Files.exists(pending.resolve(".applied"), new java.nio.file.LinkOption[0]))
/*     */       return; 
/*     */     try {
/* 117 */       copyDir(stagedConfig, liveConfig);
/* 118 */       deleteRecursively(pending);
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 127 */       if (Files.isDirectory(stagedConfig, new java.nio.file.LinkOption[0])) {
/* 128 */         Path done = pending.resolve(".applied");
/* 129 */         Files.writeString(done, Long.toString(System.currentTimeMillis()), new java.nio.file.OpenOption[0]);
/* 130 */         Log.warn("Pending profile applied but could not be cleared: " + String.valueOf(pending) + ". It will not be applied again.");
/*     */       } 
/*     */       
/* 133 */       Log.info("Applied pending profile configs to " + String.valueOf(liveConfig));
/* 134 */     } catch (IOException e) {
/* 135 */       Log.error("Failed to apply pending profile configs", e);
/*     */     } 
/*     */   }
/*     */   
/*     */   private static void copyDir(Path src, Path dst) throws IOException {
/* 140 */     Files.createDirectories(dst, (FileAttribute<?>[])new FileAttribute[0]);
/* 141 */     Stream<Path> walk = Files.walk(src, new java.nio.file.FileVisitOption[0]); 
/* 142 */     try { Objects.requireNonNull(walk); for (Path path : walk::iterator) {
/* 143 */         Path target = dst.resolve(src.relativize(path).toString());
/* 144 */         if (Files.isDirectory(path, new java.nio.file.LinkOption[0])) {
/* 145 */           Files.createDirectories(target, (FileAttribute<?>[])new FileAttribute[0]); continue;
/*     */         } 
/* 147 */         Path parent = target.getParent();
/* 148 */         if (parent != null) Files.createDirectories(parent, (FileAttribute<?>[])new FileAttribute[0]); 
/* 149 */         Files.copy(path, target, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
/*     */       } 
/*     */       
/* 152 */       if (walk != null) walk.close();  } catch (Throwable throwable) { if (walk != null)
/*     */         try { walk.close(); }
/*     */         catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
/*     */           throw throwable; }
/* 156 */      } private static void deleteRecursively(Path dir) throws IOException { if (!Files.exists(dir, new java.nio.file.LinkOption[0]))
/* 157 */       return;  Stream<Path> walk = Files.walk(dir, new java.nio.file.FileVisitOption[0]); try {
/* 158 */       walk.sorted(Comparator.reverseOrder()).forEach(p -> {
/*     */             try {
/*     */               Files.delete(p);
/* 161 */             } catch (IOException iOException) {}
/*     */           });
/*     */ 
/*     */       
/* 165 */       if (walk != null) walk.close(); 
/*     */     } catch (Throwable throwable) {
/*     */       if (walk != null)
/*     */         try {
/*     */           walk.close();
/*     */         } catch (Throwable throwable1) {
/*     */           throwable.addSuppressed(throwable1);
/*     */         }  
/*     */       throw throwable;
/*     */     }  } private static Path selfJar() {
/*     */     try {
/* 176 */       URL loc = AuroraLoader.class.getProtectionDomain().getCodeSource().getLocation();
/* 177 */       Path p = Path.of(loc.toURI());
/* 178 */       return Files.isRegularFile(p, new java.nio.file.LinkOption[0]) ? p : null;
/* 179 */     } catch (Throwable t) {
/* 180 */       return null;
/*     */     } 
/*     */   }
/*     */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\AuroraLoader.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */