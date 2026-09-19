// Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later.
#import <Cocoa/Cocoa.h>
#include <unistd.h>
#include <fcntl.h>

@interface ReplayShare : NSObject <NSApplicationDelegate, NSWindowDelegate, NSSharingServicePickerDelegate, NSSharingServiceDelegate>
@property(strong) NSWindow *window;
@property(strong) NSSharingServicePicker *picker;
@property(strong) NSSharingService *service;
@property(strong) NSURL *file;
@property BOOL finished;
@property pid_t parent;
@end
@implementation ReplayShare
- (void)finish:(int)code status:(const char *)status {
    if(self.finished)return;self.finished=YES;puts(status);fflush(stdout);exit(code);
}
- (void)applicationDidFinishLaunching:(NSNotification *)note {
    self.window=[[NSWindow alloc] initWithContentRect:NSMakeRect(0,0,420,160) styleMask:NSWindowStyleMaskTitled|NSWindowStyleMaskClosable backing:NSBackingStoreBuffered defer:NO];
    self.window.title=@"HoDoKu · 分享回放";self.window.delegate=self;self.window.releasedWhenClosed=NO;
    NSTextField *label=[NSTextField labelWithString:self.file.lastPathComponent];label.frame=NSMakeRect(20,112,380,24);self.window.contentView.autoresizingMask=NSViewWidthSizable;[self.window.contentView addSubview:label];
    NSButton *share=[NSButton buttonWithTitle:@"打开系统分享…" target:self action:@selector(showPicker:)];share.frame=NSMakeRect(20,50,210,36);[share sendActionOn:NSEventMaskLeftMouseDown];[self.window.contentView addSubview:share];
    NSButton *cancel=[NSButton buttonWithTitle:@"取消" target:self action:@selector(cancel:)];cancel.frame=NSMakeRect(270,50,120,36);cancel.keyEquivalent=@"\e";[self.window.contentView addSubview:cancel];
    [self.window center];[self.window makeKeyAndOrderFront:nil];[NSApp activateIgnoringOtherApps:YES];puts("READY");fflush(stdout);
    self.parent=getppid();fcntl(STDIN_FILENO,F_SETFL,fcntl(STDIN_FILENO,F_GETFL)|O_NONBLOCK);
    [NSTimer scheduledTimerWithTimeInterval:0.25 target:self selector:@selector(checkParent:) userInfo:nil repeats:YES];
}
- (void)checkParent:(NSTimer *)timer {
    char buf[32];ssize_t n=read(STDIN_FILENO,buf,sizeof(buf));
    if(n>0||n==0||getppid()!=self.parent)[self cancel:nil];
}
- (void)showPicker:(NSButton *)sender {
    if(self.picker||self.finished)return;
    self.picker=[[NSSharingServicePicker alloc] initWithItems:@[self.file]];self.picker.delegate=self;
    [self.picker showRelativeToRect:sender.bounds ofView:sender preferredEdge:NSRectEdgeMaxY];
    if(!self.finished){puts("OPENED");fflush(stdout);}
}
- (void)cancel:(id)sender {
    if(@available(macOS 13.0,*)){[self.picker close];}
    [self finish:2 status:"CANCELLED"];
}
- (BOOL)windowShouldClose:(NSWindow *)sender {[self cancel:nil];return NO;}
- (NSArray<NSSharingService *> *)sharingServicePicker:(NSSharingServicePicker *)picker sharingServicesForItems:(NSArray *)items proposedSharingServices:(NSArray<NSSharingService *> *)services {
    if(services.count==0){dispatch_async(dispatch_get_main_queue(),^{[self finish:3 status:"NO_SERVICES"];});}
    return services;
}
- (id<NSSharingServiceDelegate>)sharingServicePicker:(NSSharingServicePicker *)picker delegateForSharingService:(NSSharingService *)service {self.service=service;return self;}
- (void)sharingServicePicker:(NSSharingServicePicker *)picker didChooseSharingService:(NSSharingService *)service {
    if(!service){[self finish:2 status:"CANCELLED"];return;}self.service=service;puts("SELECTED");fflush(stdout);
    // Items are received by service AFTER this callback: retain helper/file until terminal callback.
}
- (void)sharingService:(NSSharingService *)service didShareItems:(NSArray *)items {[self finish:0 status:"COMPLETED"];}
- (void)sharingService:(NSSharingService *)service didFailToShareItems:(NSArray *)items error:(NSError *)error {if([error.domain isEqualToString:NSCocoaErrorDomain]&&error.code==NSUserCancelledError)[self finish:2 status:"CANCELLED"];else [self finish:5 status:"FAILED"];}
@end
int main(int argc,const char *argv[]){@autoreleasepool{
    if(argc!=2){fputs("INVALID_INPUT\n",stderr);return 4;}
    NSString *path=[[NSString alloc] initWithUTF8String:argv[1]];BOOL directory=NO;
    if(!path||![[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&directory]||directory||![[NSFileManager defaultManager] isReadableFileAtPath:path]){fputs("INVALID_INPUT\n",stderr);return 4;}
    [NSApplication sharedApplication];[NSApp setActivationPolicy:NSApplicationActivationPolicyAccessory];
    ReplayShare *delegate=[ReplayShare new];delegate.file=[NSURL fileURLWithPath:path];NSApp.delegate=delegate;[NSApp run];return 5;
}}
