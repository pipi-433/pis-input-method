package org.fcitx.fcitx5.android.pis.translation;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.nio.channels.FileLock;

public final class ModelStore {
    public static final String SHA256="dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699";
    public static final long SIZE=1133080448L;
    public static final String NAME="Hy-MT2-1.8B-Q4_K_M.gguf";
    public static final String ASSET="translation/"+NAME;
    public static File file(Context context){return new File(context.getFilesDir(),"translation/"+NAME);}
    public static boolean present(Context context){return file(context).isFile() && file(context).length()==SIZE;}
    public interface Progress { void update(long bytes); }
    public static boolean bundled(Context context){try(android.content.res.AssetFileDescriptor asset=context.getAssets().openFd(ASSET)){return asset.getLength()==SIZE;}catch(IOException missing){return false;}}
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();}
    public static void verify(File file)throws Exception{
        verify(file,()->false);
    }
    private static void verify(File file,BooleanSupplier canceled)throws Exception{
        if(file.length()!=SIZE)throw new IOException("模型尚未准备，请重试");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[1024*1024];
        try(InputStream input=new FileInputStream(file)){int n;while((n=input.read(buffer))>=0){if(canceled.getAsBoolean())throw new InterruptedIOException("模型准备已取消");digest.update(buffer,0,n);}}
        if(!SHA256.equals(hex(digest.digest())))throw new IOException("模型校验失败");
    }
    private static void directory(File target)throws IOException{if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法创建模型目录");}
    private static void copyModel(Context context,InputStream input,File temporary,File target,BooleanSupplier canceled,Progress progress)throws Exception{
        if(temporary.exists()&&!temporary.delete())throw new IOException("无法清理未完成的模型准备文件");
        if(target.getParentFile().getUsableSpace()<SIZE+32*1024*1024L)throw new IOException("模型准备需要至少1.2GB可用空间");
        long total=0,last=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileOutputStream output=new FileOutputStream(temporary)){
            if(input==null)throw new IOException("无法读取模型文件");byte[] buffer=new byte[1024*1024];int n;
            while((n=input.read(buffer))>=0){if(canceled.getAsBoolean())throw new InterruptedIOException("模型准备已取消");total+=n;if(total>SIZE)throw new IOException("模型文件不匹配");output.write(buffer,0,n);digest.update(buffer,0,n);if(total-last>16*1024*1024L){progress.update(total);last=total;}}
            output.getFD().sync();
            if(total!=SIZE||!SHA256.equals(hex(digest.digest())))throw new IOException("模型校验失败，未替换原模型");
        }catch(Exception error){temporary.delete();throw error;}
        if(canceled.getAsBoolean()){temporary.delete();throw new InterruptedIOException("模型准备已取消");}
        android.system.Os.rename(temporary.getAbsolutePath(),target.getAbsolutePath());progress.update(SIZE);
    }
    public static synchronized File ensureAvailable(Context context,BooleanSupplier canceled,Progress progress)throws Exception{
        File target=file(context);directory(target);
        try(FileOutputStream lock=new FileOutputStream(new File(target.getParentFile(),"model.lock"),true);FileLock held=lock.getChannel().lock()){
            if(canceled.getAsBoolean())throw new InterruptedIOException("模型准备已取消");
            if(present(context))try{verify(target,canceled);return target;}catch(InterruptedIOException stopped){throw stopped;}catch(IOException invalid){/* Replace only after validating the bundled copy. */}
            if(!bundled(context))throw new IOException("没有可用模型，请重新安装完整版本或导入模型");
            try(InputStream input=context.getAssets().open(ASSET,android.content.res.AssetManager.ACCESS_STREAMING)){
                copyModel(context,input,new File(target.getParentFile(),"model-bundled.part"),target,canceled,progress);
            }
            return target;
        }
    }
    public static synchronized void importModel(Context context,Uri uri,AtomicBoolean canceled,Progress progress)throws Exception{
        File target=file(context);directory(target);
        try(FileOutputStream lock=new FileOutputStream(new File(target.getParentFile(),"model.lock"),true);FileLock held=lock.getChannel().lock();InputStream input=context.getContentResolver().openInputStream(uri)){
            copyModel(context,input,new File(target.getParentFile(),"model-import.part"),target,canceled::get,progress);
        }
    }
}

