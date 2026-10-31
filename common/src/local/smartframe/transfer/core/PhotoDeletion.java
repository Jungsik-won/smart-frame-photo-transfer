package local.smartframe.transfer.core;
import java.util.*;

/** Validate the entire explicit selection before any deletion; preserve per-photo results. */
public final class PhotoDeletion {
    public interface Sink { boolean erase(String hash)throws Exception; }
    public static final class Result {
        public final List<String> deleted=new ArrayList<>(),missing=new ArrayList<>();
        public final Map<String,String> failed=new LinkedHashMap<>();
    }
    public static void requireVolume(String requested,String selected){if(requested==null||requested.isEmpty()||!requested.equals(selected))throw new IllegalArgumentException("액자 저장소가 바뀌었어요. 사진 목록을 다시 열어 주세요.");}
    public static Result delete(List<String> hashes,Sink sink){
        if(hashes==null||hashes.isEmpty()||hashes.size()>50)throw new IllegalArgumentException("삭제할 사진은 한 번에 1~50장 선택해 주세요.");
        for(String h:hashes)if(!Wire.validHash(h))throw new IllegalArgumentException("사진 선택 정보가 올바르지 않아요. 목록을 새로 열어 주세요.");
        Result result=new Result();
        for(String h:new LinkedHashSet<>(hashes))try{if(sink.erase(h))result.deleted.add(h);else result.missing.add(h);}catch(Exception e){result.failed.put(h,e.getMessage()==null?"사진을 삭제하지 못했어요.":e.getMessage());}
        return result;
    }
}
