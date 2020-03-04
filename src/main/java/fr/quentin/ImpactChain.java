package fr.quentin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * A limked list materializing the impact of an impacting element on an impacted element
 */
public class ImpactChain implements JsonSerializable<ImpactElement> {

    /**
	 *
	 */
	private ImpactChain previous;
    private ImpactElement root;
    private ImpactElement current;
    private Integer size;

    public ImpactChain(ImpactElement impactingThing) {
		this.previous = null;
        this.root = impactingThing;
        this.current = impactingThing;
        this.size = 1;
    }

    protected ImpactChain(ImpactChain last, ImpactElement content) {
		this.previous = last;
        this.root = last.getRoot();
        this.current = content;
        this.size = 1 + last.size();
    }

    public ImpactElement getRoot() {
        return root;
    }

    public Integer size() {
        return size;
    }

    public ImpactElement getLast() {
        return current;
    }

    public ImpactChain getPrevious() {
        return previous;
    }

    public ImpactChain extend(ImpactElement x) {
        return new ImpactChain(this, x);
    }

    @Override
    public String toString() {
        return "Impact [current=" + current + ", root=" + root + "]";
    }

    @Override
    public JsonElement toJson(ToJson f) {
        JsonArray a = new JsonArray();
        ImpactChain prev = this;
        a.add(size);
        while (prev != null) {
            a.add(f.apply(prev.getLast()));
            prev = prev.getPrevious();
        }
        return a;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ImpactChain) {
            ImpactChain x = (ImpactChain) obj;
            return x.getRoot().equals(getRoot()) && x.getLast().equals(getLast());
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + getRoot().hashCode();
        result = prime * result + (getLast().hashCode());
        return result;
    }
}