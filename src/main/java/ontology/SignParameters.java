package ontology;

import jade.content.Concept;

import java.util.Objects;

public class SignParameters implements Concept {
    Long _x;
    Long _y_begin;
    Long _y_end;
    Long _limit_max_speed;

    public SignParameters() {
        this._x = 1L;
        this._y_begin = 0L;
        this._y_end = 50L;
        this._limit_max_speed = 150L;
    }

    public SignParameters(Long y_start, Long y_end, Long _max_speed) {
        this._x = 1L;
        this._y_begin = y_start;
        this._y_end = y_end;
        this._limit_max_speed = _max_speed;
    }

    public Long getX() {
        return _x;
    }

    public void setX(Long _x) {
        this._x = _x;
    }

    public Long getYBegin() {
        return _y_begin;
    }

    public void setYBegin(Long _y_start) {
        this._y_begin = _y_start;
    }

    public Long getYEnd() {
        return _y_end;
    }

    public void setYEnd(Long _y_end) {
        this._y_end = _y_end;
    }

    public Long getLimitMaxSpeed() {
        return _limit_max_speed;
    }

    public void setLimitMaxSpeed(Long _max_speed) {
        this._limit_max_speed = _max_speed;
    }
}
