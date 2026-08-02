package com.skcraft.launcher.auth.skin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SkinProcessor {
	public static byte[] renderHead(byte[] skinData) throws IOException {
		BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinData));

		BufferedImage result = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = result.getGraphics();

		// Draw bottom head layer
		graphics.drawImage(skin, 0, 0, 32, 32, 8, 8, 16, 16, null);
		// Draw top head (hat) layer -- solo existe en el formato moderno de skin
		// (64x64). Las skins legacy (64x32, cuentas viejas como "Notch") no tienen
		// esta segunda capa: ese espacio del archivo viene relleno de negro solido
		// y opaco, asi que dibujarlo ahi pisaria toda la cara con un cuadrado negro.
		if (skin.getHeight() >= 64) {
			graphics.drawImage(skin, 0, 0, 32, 32, 40, 8, 48, 16, null);
		}

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(result, "png", outputStream);

		return outputStream.toByteArray();
	}
}
