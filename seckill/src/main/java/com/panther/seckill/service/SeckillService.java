package com.panther.seckill.service;

import com.panther.seckill.config.redis.RedisService;
import com.panther.seckill.config.redis.SeckillKey;
import com.panther.seckill.model.OrderInfo;
import com.panther.seckill.model.SeckillOrder;
import com.panther.seckill.model.User;
import com.panther.seckill.util.MD5Util;
import com.panther.seckill.util.UUIDUtil;
import com.panther.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * @version 1.0
 * @ClassName SeckillService
 * @Description TODO
 * @date 2019-07-25 11:13
 */
@Service
public class SeckillService {
    
    @Autowired
    GoodsService goodsService;
    
    @Autowired
    OrderService orderService;
    
    @Autowired
    RedisService redisService;

    private void setSeckillEnd(Long goodsId) {
        redisService.set(SeckillKey.seckillEnd,""+goodsId,true);
    }

    private boolean getSeckillEnd(long goodsId) {
        return redisService.exists(SeckillKey.seckillEnd,""+goodsId);
    }
    
    @Transactional
    public OrderInfo seckill(User user, GoodsVo goods) {
        //减库存
        boolean success = goodsService.reduceStock(goods);
        //下单
        if(success){
            return orderService.creatOrder(user,goods);
        }else {
            setSeckillEnd(goods.getId());
            return null;
        }
        
    }

    public long getSeckillResult(long userId, long goodsId){
        SeckillOrder seckillOrder = orderService.getSeckillOrderByUserIdGoodsId(userId,goodsId);
        if(seckillOrder != null){
            return seckillOrder.getOrderId();
        }else{
            boolean seckillStatus = getSeckillEnd(goodsId);
            if(seckillStatus){
                return -1L;
            }else {
                return 0L;
            }
        }
    }

    public boolean checkPath(String path, User user, long goodsId) {
        String oldPath = redisService.get(SeckillKey.getSeckillPath,user.getId()+"_"+goodsId,String.class);
        return oldPath.equals(path);      
    }

    public String creatPath(User user, long goodsId) {
        String str = MD5Util.md5(UUIDUtil.uuid()+"123");
        redisService.set(SeckillKey.getSeckillPath,user.getId()+"_"+goodsId,str);
        return str;
    }

    public BufferedImage creatVerifyImg(User user, long goodsId) {
        if(user == null || goodsId <=0) {
            return null;
        }
        int width = 80;
        int height = 32;
        //create the image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        // set the background color
        g.setColor(new Color(0xDCDCDC));
        g.fillRect(0, 0, width, height);
        // draw the border
        g.setColor(Color.black);
        g.drawRect(0, 0, width - 1, height - 1);
        // create a random instance to generate the codes
        Random rdm = new Random();
        // make some confusion
        for (int i = 0; i < 50; i++) {
            int x = rdm.nextInt(width);
            int y = rdm.nextInt(height);
            g.drawOval(x, y, 0, 0);
        }
        // generate a random code
        String verifyCode = generateVerifyCode(rdm);
        g.setColor(new Color(0, 100, 0));
        g.setFont(new Font("Candara", Font.BOLD, 24));
        g.drawString(verifyCode, 8, 24);
        g.dispose();
        //把验证码存到redis中
        int rnd = calc(verifyCode);
        redisService.set(SeckillKey.getSeckillVerifyCode, user.getId()+"_"+goodsId, rnd);
        //输出图片	
        return image;
    }

    private int calc(String exp) {
        try{
            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            ScriptEngine javaScript = scriptEngineManager.getEngineByName("JavaScript");
            return (Integer) javaScript.eval(exp);
        }catch (Exception e){
            e.printStackTrace();
            return 0;
        }
    }

    private static char[] ops = new char[]{'+','-','*'};
    
    /**
     * + - *
     * @param rdm
     * @return
     */
    private String generateVerifyCode(Random rdm) {
        int num1 = rdm.nextInt(10);
        int num2 = rdm.nextInt(10);
        int num3 = rdm.nextInt(10);
        char op1 = ops[rdm.nextInt(3)];
        char op2 = ops[rdm.nextInt(3)];
        String exp = "" + num1 + op1 + num2 + op2 + num3;
        return exp;
    }

    public boolean checkVerifyCode(User user, long goodsId, int verifyCode) {
        Integer integer = redisService.get(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId, Integer.class);
        if(integer == null || integer - verifyCode !=0){
            return false;
        }
        redisService.delete(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId);
        return true;
    }
}
