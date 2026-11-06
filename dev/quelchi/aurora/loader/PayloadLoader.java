/*     */ package dev.quelchi.aurora.loader;
/*     */ 
/*     */ import java.io.InputStream;
/*     */ import java.lang.reflect.Constructor;
/*     */ import java.lang.reflect.Field;
/*     */ import java.lang.reflect.Method;
/*     */ import java.nio.file.Files;
/*     */ import java.nio.file.Path;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Collection;
/*     */ import java.util.Collections;
/*     */ import java.util.Iterator;
/*     */ import java.util.List;
/*     */ import java.util.Map;
/*     */ import java.util.zip.ZipEntry;
/*     */ import java.util.zip.ZipFile;
/*     */ import net.fabricmc.loader.api.FabricLoader;
/*     */ import net.fabricmc.loader.api.ModContainer;
/*     */ import net.fabricmc.loader.api.metadata.ModDependency;
/*     */ import net.fabricmc.loader.api.metadata.ModMetadata;
/*     */ import net.fabricmc.loader.impl.launch.FabricLauncherBase;
/*     */ import org.spongepowered.asm.mixin.MixinEnvironment;
/*     */ import org.spongepowered.asm.mixin.Mixins;
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
/*     */ public final class PayloadLoader
/*     */ {
/*     */   public static Path payloadPath() {
/*  48 */     Path dir = FabricLoader.getInstance().getGameDir().resolve("aurora");
/*  49 */     Path payload = dir.resolve("aurora.jar");
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*  55 */     Path legacy = dir.resolve("aurora-payload.jar");
/*  56 */     if (!Files.isRegularFile(payload, new java.nio.file.LinkOption[0]) && Files.isRegularFile(legacy, new java.nio.file.LinkOption[0])) {
/*     */       try {
/*  58 */         Files.move(legacy, payload, new java.nio.file.CopyOption[0]);
/*  59 */       } catch (Exception exception) {}
/*     */     }
/*     */ 
/*     */     
/*  63 */     return payload;
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   public static boolean load(Path payload) {
/*  70 */     if (!Files.isRegularFile(payload, new java.nio.file.LinkOption[0])) {
/*  71 */       Log.warn("No payload at " + String.valueOf(payload) + ", Aurora will not load this launch");
/*  72 */       return false;
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/*  77 */     if (!PayloadSignature.verify(payload)) {
/*  78 */       Log.error("Payload signature is missing or does not match, Aurora will not load this launch. Only a build signed by Aurora runs under this loader.", null);
/*     */       
/*  80 */       return false;
/*     */     } 
/*     */ 
/*     */ 
/*     */     
/*  85 */     Object metadata = readMetadata(payload);
/*  86 */     if (metadata == null) {
/*  87 */       Log.error("Payload has no readable fabric.mod.json", null);
/*  88 */       return false;
/*     */     } 
/*     */     
/*  91 */     List<String[]> broken = brokenBy(metadata);
/*  92 */     if (!broken.isEmpty()) {
/*  93 */       StringBuilder list = new StringBuilder();
/*  94 */       for (String[] mod : broken) {
/*  95 */         if (list.length() > 0) list.append(", "); 
/*  96 */         list.append(mod[0]).append(" (").append(mod[1]).append(')');
/*     */       } 
/*  98 */       Log.error("Aurora is not loading this launch, it cannot run alongside " + String.valueOf(list), null);
/*  99 */       if (Notice.incompatible(broken, FabricLoader.getInstance().getGameDir().resolve("mods"))) {
/* 100 */         Log.info("Quit from the incompatible mods window, not launching the game");
/* 101 */         System.exit(0);
/*     */       } 
/* 103 */       return false;
/*     */     } 
/*     */     
/*     */     try {
/* 107 */       FabricLauncherBase.getLauncher().addToClassPath(payload, new String[0]);
/* 108 */     } catch (Throwable t) {
/* 109 */       Log.error("Could not add the payload to the class path", t);
/* 110 */       return false;
/*     */     } 
/*     */     
/* 113 */     if (!registerMixins(metadata)) return false;
/*     */     
/* 115 */     Object container = injectMod(payload, metadata);
/* 116 */     if (container == null) {
/*     */ 
/*     */ 
/*     */       
/* 120 */       Log.error("Payload is on the class path but is not a mod: assets and entrypoints will be missing", null);
/*     */       
/* 122 */       return false;
/*     */     } 
/*     */     
/* 125 */     registerEntrypoints(container, metadata);
/* 126 */     Log.info("Payload loaded from " + String.valueOf(payload));
/* 127 */     return true;
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
/*     */   private static List<String[]> brokenBy(Object metadata) {
/* 150 */     List<String[]> broken = (List)new ArrayList<>();
/*     */     try {
/* 152 */       for (Iterator<ModDependency> iterator = ((ModMetadata)metadata).getDependencies().iterator(); iterator.hasNext(); ) { ModDependency dep = iterator.next();
/* 153 */         boolean breaks = (dep.getKind() == ModDependency.Kind.BREAKS);
/* 154 */         if (!breaks && dep.getKind() != ModDependency.Kind.CONFLICTS)
/* 155 */           continue;  FabricLoader.getInstance().getModContainer(dep.getModId())
/* 156 */           .filter(mod -> dep.matches(mod.getMetadata().getVersion()))
/* 157 */           .ifPresent(mod -> {
/*     */               String name = mod.getMetadata().getName();
/*     */               
/*     */               if (breaks) {
/*     */                 broken.add(new String[] { name, dep.getModId() });
/*     */               } else {
/*     */                 Log.warn("Aurora conflicts with " + name + " (" + dep.getModId() + "), expect problems");
/*     */               } 
/*     */             }); }
/*     */     
/* 167 */     } catch (Throwable t) {
/*     */       
/* 169 */       Log.error("Could not check the payload's compatibility rules", t);
/*     */     } 
/* 171 */     return broken;
/*     */   }
/*     */ 
/*     */   
/*     */   private static Object readMetadata(Path payload) {
/*     */     
/* 177 */     try { ZipFile zip = new ZipFile(payload.toFile()); 
/* 178 */       try { ZipEntry entry = zip.getEntry("fabric.mod.json");
/*     */ 
/*     */ 
/*     */         
/* 182 */         if (entry == null) { Object object = null;
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
/* 202 */           zip.close(); return object; }  Class<?> parser = Class.forName("net.fabricmc.loader.impl.metadata.ModMetadataParser"); Class<?> versionOverrides = Class.forName("net.fabricmc.loader.impl.metadata.VersionOverrides"); Class<?> dependencyOverrides = Class.forName("net.fabricmc.loader.impl.metadata.DependencyOverrides"); Object vo = versionOverrides.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]); Constructor<?> doCtor = dependencyOverrides.getDeclaredConstructor(new Class[] { Path.class }); doCtor.setAccessible(true); Object dep = doCtor.newInstance(new Object[] { FabricLoader.getInstance().getConfigDir() }); Method parse = parser.getDeclaredMethod("parseMetadata", new Class[] { InputStream.class, String.class, List.class, versionOverrides, dependencyOverrides, boolean.class }); parse.setAccessible(true); InputStream in = zip.getInputStream(entry); try { Object object = parse.invoke(null, new Object[] { in, payload.toString(), Collections.emptyList(), vo, dep, Boolean.valueOf(false) }); if (in != null) in.close();  zip.close(); return object; } catch (Throwable throwable) { if (in != null) try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  } catch (Throwable throwable) { try { zip.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }  throw throwable; }  } catch (Throwable t)
/* 203 */     { Log.error("Could not read the payload's metadata", t);
/* 204 */       return null; }
/*     */   
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private static boolean registerMixins(Object metadata) {
/*     */     try {
/* 213 */       Class<?> loaderMetadata = Class.forName("net.fabricmc.loader.impl.metadata.LoaderModMetadata");
/* 214 */       Method getConfigs = loaderMetadata.getMethod("getMixinConfigs", new Class[] {
/* 215 */             Class.forName("net.fabricmc.api.EnvType")
/*     */           });
/* 217 */       Collection<String> configs = (Collection<String>)getConfigs.invoke(metadata, new Object[] {
/* 218 */             Enum.valueOf((Class)Class.forName("net.fabricmc.api.EnvType")
/* 219 */               .asSubclass(Enum.class), "CLIENT")
/*     */           });
/* 221 */       for (String config : configs) {
/* 222 */         Mixins.addConfiguration(config);
/* 223 */         Log.info("Registered mixin config " + config);
/*     */       } 
/* 225 */       chainLoadMixins();
/* 226 */       return true;
/* 227 */     } catch (Throwable t) {
/* 228 */       Log.error("Could not register the payload's mixin configs", t);
/* 229 */       return false;
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
/*     */   private static void chainLoadMixins() {
/*     */     try {
/* 251 */       if (Mixins.getUnvisitedCount() == 0)
/*     */         return; 
/* 253 */       MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
/* 254 */       Object transformer = env.getActiveTransformer();
/* 255 */       if (transformer == null) {
/* 256 */         Log.error("No active mixin transformer, the payload's mixins will not apply", null);
/*     */         return;
/*     */       } 
/* 259 */       Field processorField = null;
/* 260 */       for (Class<?> c = transformer.getClass(); c != null && processorField == null; 
/* 261 */         c = c.getSuperclass()) {
/*     */         try {
/* 263 */           processorField = c.getDeclaredField("processor");
/* 264 */         } catch (NoSuchFieldException noSuchFieldException) {}
/*     */       } 
/*     */ 
/*     */       
/* 268 */       if (processorField == null) {
/* 269 */         Log.error("Mixin transformer has no processor field: " + transformer
/* 270 */             .getClass().getName(), null);
/*     */         return;
/*     */       } 
/* 273 */       processorField.setAccessible(true);
/* 274 */       Object processor = processorField.get(transformer);
/* 275 */       Method select = processor.getClass().getDeclaredMethod("select", new Class[] { MixinEnvironment.class });
/*     */       
/* 277 */       select.setAccessible(true);
/* 278 */       select.invoke(processor, new Object[] { env });
/*     */       
/* 280 */       int left = Mixins.getUnvisitedCount();
/* 281 */       if (left != 0) {
/* 282 */         Log.error("Mixin still has " + left + " unvisited configs after chain loading", null);
/*     */       }
/* 284 */     } catch (Throwable t) {
/* 285 */       Log.error("Could not chain load the payload's mixins", t);
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
/*     */   private static Object injectMod(Path payload, Object metadata) {
/*     */     try {
/* 304 */       Class<?> loaderMetadata = Class.forName("net.fabricmc.loader.impl.metadata.LoaderModMetadata");
/*     */       
/* 306 */       Class<?> candidateClass = Class.forName("net.fabricmc.loader.impl.discovery.ModCandidateImpl");
/* 307 */       Method createPlain = candidateClass.getDeclaredMethod("createPlain", new Class[] { List.class, loaderMetadata, boolean.class, Collection.class });
/*     */       
/* 309 */       createPlain.setAccessible(true);
/* 310 */       Object candidate = createPlain.invoke(null, new Object[] {
/* 311 */             List.of(payload), metadata, Boolean.valueOf(false), Collections.emptyList()
/*     */           });
/* 313 */       Class<?> containerClass = Class.forName("net.fabricmc.loader.impl.ModContainerImpl");
/* 314 */       Constructor<?> ctor = containerClass.getDeclaredConstructor(new Class[] { candidateClass });
/* 315 */       ctor.setAccessible(true);
/* 316 */       Object container = ctor.newInstance(new Object[] { candidate });
/*     */       
/* 318 */       Object loader = loaderImpl();
/* 319 */       String id = (String)loaderMetadata.getMethod("getId", new Class[0]).invoke(metadata, new Object[0]);
/*     */ 
/*     */       
/* 322 */       List<Object> mods = (List<Object>)field(loader, "mods");
/*     */       
/* 324 */       Map<String, Object> modMap = (Map<String, Object>)field(loader, "modMap");
/*     */       
/* 326 */       if (modMap.containsKey(id)) {
/* 327 */         Log.error("A mod with id " + id + " is already loaded. The payload jar is probably also in the mods folder.", null);
/*     */         
/* 329 */         return null;
/*     */       } 
/* 331 */       mods.add(container);
/* 332 */       modMap.put(id, container);
/* 333 */       return container;
/* 334 */     } catch (Throwable t) {
/* 335 */       Log.error("Could not register the payload as a mod", t);
/* 336 */       return null;
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
/*     */   private static void registerEntrypoints(Object container, Object metadata) {
/*     */     try {
/* 357 */       Class<?> loaderMetadata = Class.forName("net.fabricmc.loader.impl.metadata.LoaderModMetadata");
/* 358 */       Object loader = loaderImpl();
/* 359 */       Object storage = field(loader, "entrypointStorage");
/*     */       
/* 361 */       Map<String, Object> adapters = (Map<String, Object>)field(loader, "adapterMap");
/*     */ 
/*     */ 
/*     */       
/* 365 */       Collection<String> keys = (Collection<String>)loaderMetadata.getMethod("getEntrypointKeys", new Class[0]).invoke(metadata, new Object[0]);
/* 366 */       Method getEntrypoints = loaderMetadata.getMethod("getEntrypoints", new Class[] { String.class });
/*     */ 
/*     */       
/* 369 */       Class<?> entrypointMetadata = Class.forName("net.fabricmc.loader.impl.metadata.EntrypointMetadata");
/* 370 */       Class<?> containerClass = Class.forName("net.fabricmc.loader.impl.ModContainerImpl");
/* 371 */       Method add = storage.getClass().getMethod("add", new Class[] { containerClass, String.class, entrypointMetadata, Map.class });
/*     */ 
/*     */       
/* 374 */       for (String key : keys) {
/*     */         
/* 376 */         List<Object> entries = (List<Object>)getEntrypoints.invoke(metadata, new Object[] { key });
/* 377 */         for (Object entry : entries) {
/* 378 */           add.invoke(storage, new Object[] { container, key, entry, adapters });
/* 379 */           Log.info("Registered " + key + " entrypoint from the payload");
/*     */         } 
/*     */       } 
/* 382 */     } catch (Throwable t) {
/*     */ 
/*     */       
/* 385 */       Log.error("Could not register the payload's entrypoints", t);
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private static Object loaderImpl() throws Exception {
/* 392 */     return Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl")
/* 393 */       .getField("INSTANCE").get(null);
/*     */   }
/*     */   
/*     */   private static Object field(Object owner, String name) throws Exception {
/* 397 */     Field f = owner.getClass().getDeclaredField(name);
/* 398 */     f.setAccessible(true);
/* 399 */     return f.get(owner);
/*     */   }
/*     */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\PayloadLoader.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */