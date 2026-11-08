package airgame.bridge {
    import flash.utils.Dictionary;
    import flash.utils.getTimer;
    public class SpeedClock {
        public static var multiplier:Number=1;
        public static var timers:Dictionary=new Dictionary(true);
        private static var realAnchor:int=getTimer();
        private static var virtualAnchor:Number=realAnchor;
        public static function now():int {return int(virtualAnchor+uint(getTimer()-realAnchor)*multiplier);}
        public static function setSpeed(value:Number):void {
            if(value!=0.5&&value!=1&&value!=2&&value!=3&&value!=5)throw new ArgumentError("unsupported_speed");
            virtualAnchor=now();realAnchor=getTimer();multiplier=value;
            for(var timer:Object in timers)timer.rescale();
        }
    }
}
