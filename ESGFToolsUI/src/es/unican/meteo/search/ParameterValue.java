package es.unican.meteo.search;

/**
 * Public class that represents a pair parameter - value.
 */
public class ParameterValue {
    /** Name of parameter. **/
    private Parameter parameter;

    /** Number of possible values for this facet. **/
    private Object value;

    /**
     * Constructor.
     * 
     * @param name
     *            na
     * @param value
     */
    public ParameterValue(Parameter name, Object value) {
        this.parameter = name;
        this.value = value;
    }

    /**
     * Get name of parameter.
     * 
     * @return name of facet
     */
    public Parameter getParameter() {
        return parameter;
    }

    /**
     * Get value of parameter
     * 
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (parameter == Parameter.BBOX) { // then value is array[]
            float[] bbox = (float[]) value;
            return parameter + " : " + "[" + bbox[0] + "," + bbox[1] + ","
                    + bbox[2] + "," + bbox[3] + "]";
        }
        return parameter + " : " + value;
    }
}
