package fr.quentin;

public class Position{
    String file;
    int start;
    int end;

    public Position(String filePath, int start, int end) {
        this.file = filePath;
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public String getFilePath() {
        return file;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + end;
        result = prime * result + ((file == null) ? 0 : file.hashCode());
        result = prime * result + start;
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
        if (end != other.end)
            return false;
        if (file == null) {
            if (other.file != null)
                return false;
        } else if (!file.equals(other.file))
            return false;
        if (start != other.start)
            return false;
        return true;
    }

    
}