//
//  DoraemonMockHalfButton.h
//  AFNetworking
//
//  Created by didi on 2019/10/23.
//

#import <UIKit/UIKit.h>

@protocol DoraemonMockFilterButtonDelegate<NSObject>

- (void)halfBtnClick:(id)sender;

@end

@interface DoraemonMockFilterHalfButton : UIView

@property (nonatomic, weak) id<DoraemonMockFilterButtonDelegate> delegate;

@property (nonatomic, assign) BOOL down;
@property (nonatomic, assign) NSInteger selectedItemIndex;

- (void)renderUIWithTitle:(NSString *)title;
- (void)setDropdown:(BOOL )isDown;

@end


