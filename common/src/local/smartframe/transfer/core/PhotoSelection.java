package local.smartframe.transfer.core;
import java.util.*;
/** Bounded, ordered selection; a second tap always removes, even at capacity. */
public final class PhotoSelection {
    public enum Change { ADDED, REMOVED, FULL }
    private final int limit;
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    public PhotoSelection(int limit){if(limit<1||limit>200)throw new IllegalArgumentException("Invalid limit");this.limit=limit;}
    public Change toggle(String uri){if(uri==null||uri.isEmpty())throw new IllegalArgumentException("Missing photo");if(selected.remove(uri))return Change.REMOVED;if(selected.size()>=limit)return Change.FULL;selected.add(uri);return Change.ADDED;}
    public boolean contains(String uri){return selected.contains(uri);}
    public int size(){return selected.size();}
    public ArrayList<String> values(){return new ArrayList<>(selected);}
    public void restore(Collection<String> values){selected.clear();for(String uri:values)if(uri!=null&&!uri.isEmpty()&&selected.size()<limit)selected.add(uri);}
    public void retain(Collection<String> available){selected.retainAll(available);}
}
