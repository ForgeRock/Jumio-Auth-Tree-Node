# Jumio Cloud Nodes 
This is a reference manual and configuration guide for the Netverify integration with Forgerock Identity Cloud. 

## What is Jumio ID Verification?
Jumio's ID Verification allows customers to easily and securely capture and submit their government-issued ID documents. It performs real-time ID verification using machine learning, helping you deter fraud and meet regulatory requirements. 
Jumio validates the user’s ID, corroborates it with a selfie and uses advanced liveness detection to ensure the person is actually present. 

![Image 1](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud1.png)

## Nodes Description 
- Jumio Initiate Node- This node will initiate Jumio’s NetVerify service. This service will scan a user’s ID as well as their face to verify their identity.
* Jumio Decision Node- This node will decide if the user is able to successfully login depending on the validity of their NetVerify scan. If the NetVerify service is able to successfully validate the user’s identity, they will be successfully authenticated. In the case of unreadable scans or unsupported types they will be led back to the Initiate node to redo the NetVerify process. And finally in the case of failed or fraud detection, the user will be met with a login failure. 

## Set up the Jumio Service in ForgeRock Cloud
1. Sign into the Jumio Customer Portal 
![Image2](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud2.png)

2. In the toggle menu on the left hand sign go to Settings > Managed Services > Identity Verification.
![Image3](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud3.png) 
 
3. Go to API Credentials > OAuth2 Clients
![Image4](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud4.png) 

4. Click ‘Generate a new API token’ and select both the ‘Initialize’ and ‘Retrieve & Delete’ permissions.
![Image5](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud5.png) 

5. Login to the ForgeRock Cloud tenant. 
6. In the left-hand side toggle menu select Access Management.

7. Go to ‘Realms’ them, on the left hand-side toggle menu select ‘Services’
![Image6](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud6.png) 
8. In the drop-down menu for ‘Choose a service type’ select ‘Jumio Service’.
![Image7](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud7%20.png)
9. Configure the service by filling out the fields. For ‘Token’ and ‘Secret’ use the new API credentials generated from the Jumio Service. 
![Image8](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud8%20.png)


## Configure the Tree in ForgeRock Cloud
1. Go to the ForgeRock Cloud homepage > Journeys > + New Journey
2. Name the Journey ‘i.e. JumioTest’ for Identity Object select ‘Alpha realm-    Users managed/alpha_user
3. Drag & drop the nodes to recreate the below tree:
![Image9](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud9.png)

## Testing
1. To test the Journey, copy and paste the preview URL in an incognito window. 
![Image10](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud10.png) 
3. Login using ForgeRock Cloud credentials and Jumio’s NetVerify Service will prompt the user to start the verification process. Click Start.
![Image11](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud11%20.png)

3. The user will be directed to select their Region and to select ID type. 
![Image12](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud12.png)

4. Take a photo of the front and back of the ID using the webcam or upload a photo from your computer
![Image13](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud13.png)
![Image14](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud14.png)

5. Once prompted, complete the face verification step.
![Image15](https://github.com/Stuti-Rana/Jumio-Auth-Tree-Node/blob/patch-1/guideimages/Jumio_Cloud15.png) 

6. If the verification is successful the user will be successfully authenticated.
