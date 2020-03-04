package fr.quentin;

import com.google.gson.JsonElement;

public interface JsonSerializable<T> {
    public default JsonElement toJson(){
        return toJson(
            ToJson.StaticIntance
        );
    }
    public default JsonElement toJson(ToJson f){
        return ToJson.StaticIntance.apply(this);
    }
}
