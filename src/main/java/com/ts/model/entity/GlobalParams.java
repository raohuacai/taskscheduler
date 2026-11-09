/*
 * Description: Entity for the global-parameters record
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;
/**
 * Entity representing the single global-parameters record that holds system-wide
 * configuration values as a JSON string.
 */
public class GlobalParams {
    private String pname;
    private String pvalue;

    // Getters and setters
    /**
     * Returns the pname.
     *
     * @return the result as a String
     */
    public String getPname() {
        return pname;
    }

    /**
     * Sets the pname.
     *
     * @param pname  pname name
     */
    public void setPname(String pname) {
        this.pname = pname;
    }

    /**
     * Returns the pvalue.
     *
     * @return the result as a String
     */
    public String getPvalue() {
        return pvalue;
    }

    /**
     * Sets the pvalue.
     *
     * @param pvalue  pvalue value
     */
    public void setPvalue(String pvalue) {
        this.pvalue = pvalue;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "GlobalParams{" +
                "pname='" + pname + '\'' +
                ", pvalue='" + pvalue + '\'' +
                '}';
    }

}