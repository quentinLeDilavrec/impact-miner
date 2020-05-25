package fr.quentin.impactMiner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Position implements JsonSerializable {
    String file;
    int start;
    int end;

    public Position(String filePath, int start, int end) {
        this.file = filePath;
        this.start = start;
        this.end = end;
    }

    // public String getRoot() {
    // return root;
    // }

    public String getFilePath() {
        return file;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((file == null) ? 0 : (file).hashCode());
        result = prime * result + start;
        result = prime * result + end;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Position other = (Position) obj;
        if (file == null) {
            if (other.file != null)
                return false;
        } else if (!(file).equals(other.file))
            return false;
        if (end != other.end)
            return false;
        if (start != other.start)
            return false;
        return true;
    }

    @Override
    public JsonElement toJson() {
        JsonObject r = new JsonObject();
        r.addProperty("file", file);
        r.addProperty("start", start);
        r.addProperty("end", end);
        return r;
    }
}