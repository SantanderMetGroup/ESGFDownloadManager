package search.management;

import java.io.Serializable;

/**
 * Public class that represents a faced value, with facet value datasets count
 * and state (selected or not)
 */
public class SearchCategoryValue implements Serializable {
    /** Name of facet value. **/
    private String value;

    /** Number of possible values for this facet. **/
    private int count;

    /** Is selected or not. */
    boolean selected;

    /** Constructor. */
    public SearchCategoryValue() {
        value = "";
        count = 0;
        selected = false;
    }

    /** Constructor. */
    public SearchCategoryValue(String name, int count, boolean selected) {
        this.value = name;
        this.count = count;
        this.selected = selected;
    }

    /**
     * Get value of facet.
     * 
     * @return value of facet
     */
    public String getValue() {
        return value;
    }

    /**
     * Set name of facet.
     * 
     * @param value
     *            of facet
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Get count of possible values for this facet.
     * 
     * @return count of possible values for this facet
     */
    public int getCount() {
        return count;
    }

    /**
     * Set count of possible values for this facet.
     * 
     * @param count
     *            of possible values for this facet
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * Return true if this facet value is selected by user or false otherwise
     * 
     * @return true if this facet value is selected by user or false otherwise
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Set if this facet value is selected by user
     * 
     * @param selected
     *            true if this facet value is selected by user or false
     *            otherwise
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return value + " (" + count + ")";
    }

}
