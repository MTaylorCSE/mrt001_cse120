#include "syscall.h"

int main(){

    printf("Creating the file\n");
    int fd = creat("jerry");
    if(fd >= 0){
        printf("did it!\n");
        char *str = "Sed eu libero sit amet est tempor cursus. Nulla ultricies nisi id libero sodales, sit amet dignissim nulla consequat. Nunc eu diam in ligula euismod dapibus. Aliquam dictum pharetra metus pellentesque dignissim. Donec ac orci est. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Ut eu euismod odio, sit amet sagittis tortor. Donec tellus justo, facilisis eget nibh a, sodales iaculis nulla. Integer in erat vel mi lacinia dignissim. Integer eleifend, mi quis efficitur venenatis, leo ipsum imperdiet nisl, a viverra ipsum augue lobortis massa. Sed tincidunt bibendum magna sed lacinia. Nulla suscipit tempus vulputate. Etiam aliquet est eu elementum tristique. Praesent diam tellus, fringilla at risus a, luctus vestibulum est. Fusce blandit mauris a porta tincidunt. Fusce et ante eu arcu volutpat lacinia. Nunc consequat vestibulum nibh sed facilisis. Duis semper lorem ac enim semper, in varius eros pulvinar. Ut bibendum purus turpis, non rhoncus lorem auctor sit amet. Fusce metus";
        int len = strlen(str);
        printf("%d long\n",len);
        write(fd,str,len);
        halt();
    } else {
        printf("failed\n");
        halt();
    }
    return 0;
}

