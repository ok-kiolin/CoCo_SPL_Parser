package spl.types;

public enum NumericMode {
    INT("int"), FLOAT("float");

    public final String cType;
    
    NumericMode(String cType){
        this.cType = cType;
    }
}
