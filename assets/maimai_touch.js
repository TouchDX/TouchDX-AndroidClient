document.addEventListener('DOMContentLoaded', function() {
    const pressColor = '#FF0000'; // Red
    const releaseColor = '#000066'; // Original blue

    const buttons = document.querySelectorAll('path[id^="button_"]');

    buttons.forEach(button => {
        button.addEventListener('touchstart', function(event) {
            event.preventDefault();
            this.setAttribute('fill', pressColor);
            Android.onButtonPress(this.id);
        });

        button.addEventListener('touchend', function(event) {
            event.preventDefault();
            this.setAttribute('fill', releaseColor);
            Android.onButtonRelease(this.id);
        });
    });
});
