package airgame.bridge {
    import flash.utils.Dictionary;
    public class Timer extends flash.utils.Timer {
        private var originalDelay:Number;
        public function Timer(delay:Number,repeatCount:int=0){super(delay/SpeedClock.multiplier,repeatCount);originalDelay=delay;SpeedClock.timers[this]=true;}
        override public function get delay():Number {return originalDelay;}
        override public function set delay(value:Number):void {super.delay=value/SpeedClock.multiplier;originalDelay=value;}
        public function rescale():void {super.delay=originalDelay/SpeedClock.multiplier;}
    }
}
