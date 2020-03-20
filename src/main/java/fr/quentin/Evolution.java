package fr.quentin;

import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public interface Evolution<T> extends JsonSerializable {
    public Set<Position> getImpactingPositions();
    public Set<Position> getPostEvolutionPositions();
    public T getOriginal();
    public String getCommitId();

    @Override
    default public JsonObject toJson() {
        JsonObject r = new JsonObject();
        r.add("type", JsonNull.INSTANCE);
        r.addProperty("commitId", getCommitId());
        JsonArray before = new JsonArray();
        for (Position p : getImpactingPositions()) {
            before.add(p.toJson());
        }
        r.add("before", before);
        JsonArray after = new JsonArray();
        for (Position p : getPostEvolutionPositions()) {
            after.add(p.toJson());
        }
        r.add("after", after);
        return r;
    }

}
