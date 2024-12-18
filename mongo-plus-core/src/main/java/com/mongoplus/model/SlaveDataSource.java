package com.mongoplus.model;

/**
 * 从配置
 * @author anwen
 * @date 2024/8/2 上午1:01
 */
public class SlaveDataSource extends BaseProperty {
    /**
     * 数据源名称,他应该是唯一的，不可重复
     **/
    private String slaveName;

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof SlaveDataSource)) {
            return false;
        } else {
            SlaveDataSource other = (SlaveDataSource)o;
            if (!other.canEqual(this)) {
                return false;
            } else if (!super.equals(o)) {
                return false;
            } else {
                Object this$slaveName = this.getSlaveName();
                Object other$slaveName = other.getSlaveName();
                if (this$slaveName == null) {
                    if (other$slaveName != null) {
                        return false;
                    }
                } else if (!this$slaveName.equals(other$slaveName)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof SlaveDataSource;
    }

    public int hashCode() {
        int result = super.hashCode();
        Object $slaveName = this.getSlaveName();
        result = result * 59 + ($slaveName == null ? 43 : $slaveName.hashCode());
        return result;
    }

    public String getSlaveName() {
        return this.slaveName;
    }

    public void setSlaveName(String slaveName) {
        this.slaveName = slaveName;
    }

    public String toString() {
        return "SlaveDataSource(slaveName=" + this.getSlaveName() + ")";
    }

    public SlaveDataSource(String slaveName) {
        this.slaveName = slaveName;
    }

    public SlaveDataSource() {
    }
}
