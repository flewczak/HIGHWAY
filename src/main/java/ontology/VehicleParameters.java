package ontology;

import jade.content.Concept;

import java.util.Objects;

public class VehicleParameters implements Concept {
    Long  _x;
    Long _y;
    Long  _speed;
    Long _max_speed;
    Long _acceleration;
    Long _max_speed_of_sign;

    public VehicleParameters(){
        this._x = 1L;
        this._y = 0L;
        this._speed = 50L;
        this._max_speed = 150L;
        this._acceleration=0L;
        this._max_speed_of_sign=150L;
    }

    public VehicleParameters(Long speed, Long maxSpeed, Long x){
        this._x = x;
        this._y = 0L;
        this._speed = speed;
        this._max_speed = maxSpeed;
        this._acceleration=0L;
        this._max_speed_of_sign=maxSpeed;
    }

    public Long getMaxSpeedOfSign() {
        return  _max_speed_of_sign;
    }

    public void setMaxSpeedOfSign(Long max_speed_of_sign ) {
        this._max_speed_of_sign=max_speed_of_sign;
    }

    public Long getX() {
        return _x;
    }

    public void setX(Long x) {
        this._x = x;
    }

    public void addY(Long y){
        _y += y;
    }

    public void addSpeed(Long speed){
        _speed += speed;
    }

    public void updateSpeed(){
        _speed += _acceleration;
    }

    public void updateY(Long interval){
        _y += _speed/interval;
    }

    public void setPercentageAcceleration(Long percent){
        if(_speed == 0L){
            _speed += getMaxSpeed()*2/10;
        }
        _acceleration = _speed*percent/100;
    }

    public void addAcceleration(Long acc){
        _acceleration +=acc;
    }

    public void addPercentageAcceleration(Long percent){
        if(_speed == 0L){
            _speed += getMaxSpeed()*2/10;
        }
        _acceleration += _speed*percent/100;
    }

    public Long getY() {
        return _y;
    }

    public void setY(Long y) {
        this._y = y;
    }

    public Long getSpeed() {
        return _speed;
    }

    public void setSpeed(Long speed) {
        this._speed = speed;
    }

    public Long getMaxSpeed() {
        return Math.min(_max_speed, _max_speed_of_sign);
}

    public void setMaxSpeed(Long max_speed) {
        this._max_speed = max_speed;
    }

    public Long getAcceleration() {
        return _acceleration;
    }

    public void setAcceleration(Long _acceleration) {
        this._acceleration = _acceleration;
    }

    public boolean equals(VehicleParameters obj) {
        return (Objects.equals(obj._y, this._y) &&
                Objects.equals(obj._x, this._x) &&
                Objects.equals(obj._max_speed, this._max_speed) &&
                Objects.equals(obj._speed, this._speed) &&
                Objects.equals(obj._acceleration, this._acceleration));
    }
}
