package com.campus.adblock.ui;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

/** Samples only the content sibling, preventing recursive capture of the bottom bar. */
public final class GlassStrip extends View {
    private final UiKit ui;private final View source;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();private final Path clip=new Path(),highlight=new Path();private Renderer renderer;
    public GlassStrip(UiKit kit,View content){super(kit.context);ui=kit;source=content;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void refresh(){invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight(),r=h/2f;paint.setStyle(Paint.Style.FILL);
        for(int i=6;i>=1;i--){paint.setColor(ui.color("glass_shadow"));paint.setAlpha(Math.max(1,Math.round(20f*(7-i)/21f)));rect.set(-ui.dp(i*.3f),ui.dp(i*.45f),w+ui.dp(i*.3f),h+ui.dp(i));canvas.drawRoundRect(rect,r,r,paint);}
        rect.set(0,0,w,h);clip.reset();clip.addRoundRect(rect,r,r,Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);
        boolean blurred=false;if(Build.VERSION.SDK_INT>=31&&canvas.isHardwareAccelerated()){if(renderer==null)renderer=new Api31Renderer(ui);blurred=renderer.draw(canvas,source,this);}
        paint.setAlpha(255);paint.setColor(ui.color(ui.highContrast()?"glass_contrast":blurred?"glass_tint":"glass_fallback"));canvas.drawRoundRect(rect,r,r,paint);
        paint.setColor(ui.color("glass_grain"));for(int y=ui.dp(3);y<h;y+=ui.dp(7))for(int x=ui.dp(4)+(y%3);x<w;x+=ui.dp(11))canvas.drawPoint(x,y,paint);
        canvas.restoreToCount(save);paint.setColor(ui.color("glass_stroke"));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(1));
        float edge=ui.dp(.5f);highlight.reset();
        rect.set(edge,edge,h-edge,h-edge);highlight.arcTo(rect,135,135);
        highlight.lineTo(w-r,edge);rect.set(w-h+edge,edge,w-edge,h-edge);highlight.arcTo(rect,270,135);
        canvas.drawPath(highlight,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDetachedFromWindow(){if(renderer!=null){renderer.release();renderer=null;}super.onDetachedFromWindow();}
    private interface Renderer{boolean draw(Canvas c,View source,View target);void release();}
    /** This implementation is never loaded on API 24-30. */
    private static final class Api31Renderer implements Renderer{
        private final android.graphics.RenderNode node=new android.graphics.RenderNode("campus-glass-strip");private final int padding;private final int[] s=new int[2],t=new int[2];
        Api31Renderer(UiKit ui){int radius=ui.dim("glass_blur");padding=radius*3;node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius,radius,android.graphics.Shader.TileMode.CLAMP));}
        @Override public boolean draw(Canvas c,View source,View target){
            if(source.getWidth()==0||target.getWidth()==0)return false;source.getLocationInWindow(s);target.getLocationInWindow(t);
            int w=target.getWidth(),h=target.getHeight();node.setPosition(-padding,-padding,w+padding,h+padding);
            android.graphics.RecordingCanvas recording=node.beginRecording(w+padding*2,h+padding*2);
            try{recording.translate(padding-(t[0]-s[0]),padding-(t[1]-s[1]));source.draw(recording);}finally{node.endRecording();}
            c.drawRenderNode(node);return true;
        }
        @Override public void release(){node.discardDisplayList();}
    }
}
