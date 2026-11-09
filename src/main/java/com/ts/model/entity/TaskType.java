/*
 * Description: Entity mapping a task-type name to its implementing Java class
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;
/**
 * Entity representing a task-type registry entry that maps a logical type name
 * (e.g., {@code SHELL}, {@code HTTP}) to a fully-qualified Java class name.
 */
public class TaskType {
    private String typeName;        // Primary key of the task type
    private String typeGroup;       // Task category group (e.g. TIMEOUT, SCRIPT, SQL, WORKFLOW, UTIL)
    private String typeDesc;        // Human-readable description of the task type
    private String className;       // Fully qualified Java class name that implements this task type
    private String pvalue;          // the parameters of this task
    private String taskColor;
    private String interruptableFlag; // Flag indicating whether the task can be interrupted; Y=yes (default), N=no
    private Date creationDate;      // Timestamp when this record was created

    // Getters and setters
    /**
     * Returns the type name.
     *
     * @return the result as a String
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Sets the type name.
     *
     * @param typeName  typeName name
     */
    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    /**
     * Returns the type group.
     *
     * @return the result as a String
     */
    public String getTypeGroup() {
        return typeGroup;
    }

    /**
     * Sets the type group.
     *
     * @param typeGroup  type discriminator
     */
    public void setTypeGroup(String typeGroup) {
        this.typeGroup = typeGroup;
    }

    /**
     * Returns the type desc.
     *
     * @return the result as a String
     */
    public String getTypeDesc() {
        return typeDesc;
    }

    /**
     * Sets the type desc.
     *
     * @param typeDesc  type discriminator
     */
    public void setTypeDesc(String typeDesc) {
        this.typeDesc = typeDesc;
    }

    /**
     * Returns the class name.
     *
     * @return the result as a String
     */
    public String getClassName() {
        return className;
    }

    /**
     * Sets the class name.
     *
     * @param className  className name
     */
    public void setClassName(String className) {
        this.className = className;
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

    /**
     * Returns the task color.
     *
     * @param getInterruptableFlag(  flag value (Y or N)
     * @return the result as a String
     */
    public String getTaskColor() {return taskColor;}

    public void setTaskColor(String taskColor) {this.taskColor = taskColor;}

    public String getInterruptableFlag() {
        return interruptableFlag;
    }

    /**
     * Sets the interruptable flag.
     *
     * @param interruptableFlag  flag value (Y or N)
     */
    public void setInterruptableFlag(String interruptableFlag) {
        this.interruptableFlag = interruptableFlag;
    }

    /**
     * Returns the creation date.
     *
     * @return the requested creationdate object, or null if not found
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date.
     *
     * @param creationDate  date/time value
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "TaskType{" +
                "typeName='" + typeName + '\'' +
                ", typeGroup='" + typeGroup + '\'' +
                ", typeDesc='" + typeDesc + '\'' +
                ", className='" + className + '\'' +
                ", interruptableFlag='" + interruptableFlag + '\'' +
                ", creationDate=" + creationDate +
                '}';
    }
}
