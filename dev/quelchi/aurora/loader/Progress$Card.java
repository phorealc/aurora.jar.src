/*     */ package dev.quelchi.aurora.loader;
/*     */ 
/*     */ import java.awt.BasicStroke;
/*     */ import java.awt.Color;
/*     */ import java.awt.GradientPaint;
/*     */ import java.awt.Graphics;
/*     */ import java.awt.Graphics2D;
/*     */ import java.awt.RadialGradientPaint;
/*     */ import java.awt.RenderingHints;
/*     */ import java.awt.event.ActionEvent;
/*     */ import java.awt.geom.Point2D;
/*     */ import java.awt.geom.RoundRectangle2D;
/*     */ import java.awt.image.BufferedImage;
/*     */ import java.io.InputStream;
/*     */ import javax.imageio.ImageIO;
/*     */ import javax.swing.JComponent;
/*     */ import javax.swing.Timer;
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
/*     */ final class Card
/*     */   extends JComponent
/*     */ {
/* 155 */   volatile String status = "Checking for updates";
/* 156 */   volatile int target = 0;
/* 157 */   private double shown = 0.0D;
/* 158 */   private BufferedImage icon = loadIcon();
/*     */ 
/*     */ 
/*     */   
/*     */   Card() {
/* 163 */     (new Timer(16, e -> {
/*     */           double d = this.target - this.shown;
/*     */           if (Math.abs(d) > 0.1D) {
/*     */             this.shown += d * 0.18D;
/*     */             repaint();
/*     */           } else if (this.shown != this.target) {
/*     */             this.shown = this.target;
/*     */             repaint();
/*     */           } 
/* 172 */         })).start();
/*     */   }
/*     */ 
/*     */   
/*     */   protected void paintComponent(Graphics g0) {
/* 177 */     Graphics2D g = (Graphics2D)g0.create();
/*     */     try {
/* 179 */       g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
/* 180 */       g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
/*     */       
/* 182 */       g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
/*     */       
/* 184 */       g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
/* 185 */       g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
/*     */       
/* 187 */       g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
/*     */       
/* 189 */       int w = getWidth(), h = getHeight();
/*     */ 
/*     */       
/* 192 */       RoundRectangle2D body = new RoundRectangle2D.Double(0.0D, 0.0D, w, h, 28.0D, 28.0D);
/* 193 */       g.setPaint(new GradientPaint(0.0F, 0.0F, Progress.BG_TOP, 0.0F, h, Progress.BG_BOT));
/* 194 */       g.fill(body);
/*     */ 
/*     */       
/* 197 */       g.setPaint(new RadialGradientPaint(new Point2D.Float(34.0F, 40.0F), 90.0F, new float[] { 0.0F, 1.0F }, new Color[] { new Color(139, 124, 246, 30), new Color(139, 124, 246, 0) }));
/*     */ 
/*     */ 
/*     */       
/* 201 */       g.fill(body);
/*     */ 
/*     */       
/* 204 */       g.setColor(Progress.RIM);
/* 205 */       g.setStroke(new BasicStroke(1.0F));
/* 206 */       g.draw(new RoundRectangle2D.Double(0.5D, 0.5D, (w - 1), (h - 1), 27.0D, 27.0D));
/*     */       
/* 208 */       int pad = 22;
/*     */ 
/*     */       
/* 211 */       int iconSize = 34, iconY = 22;
/* 212 */       if (this.icon != null) {
/* 213 */         g.drawImage(this.icon, pad, iconY, iconSize, iconSize, null);
/*     */       } else {
/* 215 */         g.setColor(Progress.ACCENT);
/* 216 */         g.fillRoundRect(pad, iconY, iconSize, iconSize, 10, 10);
/*     */       } 
/*     */       
/* 219 */       int textX = pad + iconSize + 14;
/* 220 */       g.setColor(Progress.TEXT);
/* 221 */       g.setFont(Progress.TITLE_FONT);
/* 222 */       g.drawString("Aurora", textX, iconY + 16);
/*     */       
/* 224 */       g.setColor(Progress.TEXT_SUB);
/* 225 */       g.setFont(Progress.BODY_FONT);
/* 226 */       String dots = (this.status == null) ? "" : this.status;
/* 227 */       g.drawString(dots + "…", textX, iconY + 33);
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */       
/* 234 */       int barH = 4, barX = pad, barW = w - pad * 2, barY = h - 30;
/* 235 */       g.setColor(new Color(255, 255, 255, 26));
/* 236 */       g.fillRoundRect(barX, barY, barW, barH, barH, barH);
/*     */       
/* 238 */       double frac = Math.max(0.0D, Math.min(1.0D, this.shown / 100.0D));
/* 239 */       int fw = (int)Math.round(barW * frac);
/* 240 */       if (fw > 0) {
/* 241 */         g.setPaint(new GradientPaint(barX, 0.0F, Progress.BAR_FROM, (barX + Math.max(fw, barH)), 0.0F, Progress.BAR_TO));
/* 242 */         g.fillRoundRect(barX, barY, Math.max(fw, barH), barH, barH, barH);
/*     */ 
/*     */         
/* 245 */         float breathe = 0.85F + 0.15F * (float)Math.sin((
/* 246 */             (float)System.currentTimeMillis() / 1000.0F * 1.6F));
/* 247 */         int hx = barX + fw, hy = barY + barH / 2, gsz = 26;
/* 248 */         g.setPaint(new RadialGradientPaint(new Point2D.Float(hx, hy), gsz / 2.0F, new float[] { 0.0F, 1.0F }, new Color[] { new Color(139, 124, 246, 
/*     */ 
/*     */                   
/* 251 */                   Math.round(150.0F * breathe)), new Color(139, 124, 246, 0) }));
/*     */         
/* 253 */         g.fillOval(hx - gsz / 2, hy - gsz / 2, gsz, gsz);
/*     */       } 
/*     */       
/* 256 */       g.setColor(Progress.TEXT_SUB);
/* 257 */       g.setFont(Progress.SMALL_FONT);
/* 258 */       String pct = "" + (int)Math.round(this.shown) + "%";
/* 259 */       int pw = g.getFontMetrics().stringWidth(pct);
/* 260 */       g.drawString(pct, barX + barW - pw, barY - 8);
/*     */     } finally {
/* 262 */       g.dispose();
/*     */     } 
/*     */   }
/*     */   private static BufferedImage loadIcon() {
/*     */     
/* 267 */     try { InputStream in = Card.class.getResourceAsStream("/assets/auroraloader/icon.png");
/*     */       
/* 269 */       try { BufferedImage bufferedImage = (in == null) ? null : ImageIO.read(in);
/* 270 */         if (in != null) in.close();  return bufferedImage; } catch (Throwable throwable) { if (in != null) try { in.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }   throw throwable; }  } catch (Throwable t)
/* 271 */     { return null; }
/*     */   
/*     */   }
/*     */ }


/* Location:              C:\Users\rapha\Downloads\aurora.jar!\dev\quelchi\aurora\loader\Progress$Card.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */