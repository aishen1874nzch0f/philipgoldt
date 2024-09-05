//
//  DoraemonMockUploadListView.m
//  AFNetworking
//
//  Created by didi on 2019/11/15.
//

#import "DoraemonMockUploadListView.h"
#import "DoraemonMockUploadCell.h"

@interface DoraemonMockUploadListView()

@end

@implementation DoraemonMockUploadListView

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        self.dataArray = [DoraemonMockManager sharedInstance].upLoadArray;
    }
    return self;
}


- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    DoraemonMockBaseModel* model = [self.dataArray objectAtIndex:indexPath.row];
    return [DoraemonMockUploadCell cellHeightWith:model];
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    static NSString *identifer = @"detailcell";
    DoraemonMockUploadCell *cell = [tableView dequeueReusableCellWithIdentifier:identifer];
    if (!cell) {
        cell = [[DoraemonMockUploadCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:identifer];
        cell.delegate = self;
    }
    DoraemonMockBaseModel* model = [self.dataArray objectAtIndex:indexPath.row];
    [cell renderCellWithData:model];
    return cell;
}

@end
